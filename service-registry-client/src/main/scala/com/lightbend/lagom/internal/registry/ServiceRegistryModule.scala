/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.registry

import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }
import javax.inject.{ Provider, Inject, Singleton }

import akka.stream.Materializer
import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.client.{ CircuitBreaker, ServiceClientLoader, ServiceClientImplementor, WebSocketClient }
import com.lightbend.lagom.javadsl.api.transport.NotFound
import com.lightbend.lagom.javadsl.api.{ ServiceLocator, ServiceInfo }
import com.lightbend.lagom.javadsl.jackson.{ JacksonExceptionSerializer, JacksonSerializerFactory }
import play.api.{ Mode, Configuration, Environment, Logger }
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class ServiceRegistryModule(environment: Environment, configuration: Configuration) extends AbstractModule {
  private val logger = Logger(this.getClass)

  override def configure(): Unit = {
    if (ServiceRegistryModule.isServiceRegistryServiceLocatorEnabled(configuration)) {
      if (environment.mode == Mode.Dev) {
        bind(classOf[ServiceRegistryServiceLocator.ServiceLocatorConfig]).toInstance(createDevServiceLocatorConfig)
        bind(classOf[ServiceRegistry]).toProvider(new ServiceRegistryClientProvider)
        bind(classOf[ServiceLocator]).to(classOf[ServiceRegistryServiceLocator])
        logger.debug {
          s"Running in ${environment.mode} mode. The ${classOf[ServiceLocator].getName} interface was " +
            "bound to an implementation that will query the embedded Service Locator. This is fine to use " +
            "only during development."
        }
      } else if (environment.mode == Mode.Test) {
        logger.debug {
          s"Running in ${environment.mode} mode, the ${classOf[ServiceLocator].getName} is " +
            s"bound to the class ${classOf[NoServiceLocator].getName}. That is a dummy implementation that " +
            "always fails to locate a service, which is usually fine to use in unit tests."
        }
        bind(classOf[ServiceLocator]).to(classOf[NoServiceLocator])
      } else
        logger.debug {
          s"Running in ${environment.mode} mode, hence Lagom is not binding the ${classOf[ServiceLocator].getName} " +
            "interface to a default concrete implementation as it's expected that the production " +
            "environment you are using provides a custom implementation of this interface"
        }
    } else
      logger.debug {
        s"Service locator was explicitly disabled via the configuration key ${ServiceRegistryModule.ServiceLocatorEnabledKey}."
      }
  }

  protected def createDevServiceLocatorConfig: ServiceRegistryServiceLocator.ServiceLocatorConfig = {
    val serviceLocatorURLKey = "lagom.service-locator.url"
    val config = configuration.underlying
    val url = config.getString(serviceLocatorURLKey)
    ServiceRegistryServiceLocator.ServiceLocatorConfig(new URI(url))
  }
}

object ServiceRegistryModule {
  private final val ServiceLocatorEnabledKey: String = "lagom.service-locator.enabled"
  def isServiceRegistryServiceLocatorEnabled(configuration: Configuration): Boolean = {
    configuration.underlying.getBoolean(ServiceLocatorEnabledKey)
  }
}

/**
 * This is needed to break the circular dependency between the ServiceRegistry and the ServiceLocator.
 */
@Singleton
class ServiceRegistryClientProvider extends Provider[ServiceRegistry] {
  @Inject private var config: ServiceRegistryServiceLocator.ServiceLocatorConfig = _
  @Inject private var ws: WSClient = _
  @Inject private var webSocketClient: WebSocketClient = _
  @Inject private var serviceInfo: ServiceInfo = _
  @Inject private var environment: Environment = _
  @Inject private var ec: ExecutionContext = _
  @Inject private var mat: Materializer = _
  @Inject private var circuitBreaker: CircuitBreaker = _

  @Inject private var jacksonSerializerFactory: JacksonSerializerFactory = _
  @Inject private var jacksonExceptionSerializer: JacksonExceptionSerializer = _

  lazy val get = {
    val serviceLocator = new ClientServiceLocator(config)
    val implementor = new ServiceClientImplementor(ws, webSocketClient, serviceInfo, serviceLocator,
      circuitBreaker, environment)(ec, mat)
    val loader = new ServiceClientLoader(jacksonSerializerFactory, jacksonExceptionSerializer, environment, implementor)
    loader.loadServiceClient(classOf[ServiceRegistry])
  }

  /**
   * The service locator implementation used by the ServiceRegistry's client implementation.
   */
  private class ClientServiceLocator(config: ServiceRegistryServiceLocator.ServiceLocatorConfig) extends BaseServiceLocator {
    override protected def lookup(name: String): Future[Optional[URI]] = {
      require(name == ServiceRegistry.SERVICE_NAME)
      Future.successful(Optional.of(config.url))
    }
  }
}

@Singleton
class ServiceRegistryServiceLocator @Inject() (registry: ServiceRegistry, config: ServiceRegistryServiceLocator.ServiceLocatorConfig) extends BaseServiceLocator {

  private val logger: Logger = Logger(this.getClass())

  override protected def lookup(name: String): Future[Optional[URI]] = {
    require(name != ServiceRegistry.SERVICE_NAME)
    logger.debug(s"Locating service name=[$name] ...")

    import scala.concurrent.ExecutionContext.Implicits.global
    val location: Future[Optional[URI]] = {
      // FIXME: Horrible Java8/Scala code, speak up if you have a better implementation.
      val string2uri: String => Optional[URI] = uri => {
        try Optional.of(new URI(uri))
        catch {
          case e: java.net.URISyntaxException =>
            logger.error(s"Invalid address=[$uri] returned for service name=[$name]", e)
            Optional.empty()
          case _: NullPointerException =>
            logger.error(s"Null address returned for service name=[$name]")
            Optional.empty()
        }
      }
      import scala.compat.java8.FutureConverters._
      registry.lookup().invoke(name, null).toScala.map(string2uri).recover {
        case notFound: NotFound => Optional.empty()
      }
    }
    location.onComplete {
      case Success(address) =>
        if (address.isPresent()) logger.debug(s"Service name=[$name] can be reached at address=[${address.get.getPath}]")
        else logger.warn(s"Service name=[$name] was not found. Hint: Maybe it was not registered?")
      case Failure(e) => logger.warn(s"The service locator replied with an error when looking up the service name=[$name] address", e)
    }
    location
  }
}

abstract class BaseServiceLocator extends ServiceLocator {
  import scala.compat.java8.FutureConverters._
  import scala.concurrent.ExecutionContext.Implicits.global

  override def locate(name: String): CompletionStage[Optional[URI]] = lookup(name).toJava

  override def doWithService[T](name: String, block: JFunction[URI, CompletionStage[T]]): CompletionStage[Optional[T]] = {
    val maybeLocation = lookup(name)
    maybeLocation.flatMap(maybeURI => {
      if (maybeURI.isPresent()) block.apply(maybeURI.get()).toScala.map(Optional.of(_))
      else Future.successful(Optional.empty[T])
    }).toJava
  }

  protected def lookup(name: String): Future[Optional[URI]]
}

object ServiceRegistryServiceLocator {
  case class ServiceLocatorConfig(url: URI)
}

/**
 * An implementation of the service locator that always fails to locate the passed service's `name`.
 */
class NoServiceLocator extends ServiceLocator {
  import java.util.concurrent.CompletableFuture

  override def doWithService[T](name: String, block: JFunction[URI, CompletionStage[T]]): CompletionStage[Optional[T]] =
    CompletableFuture.completedFuture(Optional.empty())

  override def locate(name: String): CompletionStage[Optional[URI]] =
    CompletableFuture.completedFuture(Optional.empty())
}
