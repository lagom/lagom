/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.registry

import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery
import akka.stream.Materializer
import com.lightbend.lagom.internal.javadsl.api.broker.NoTopicFactoryProvider
import com.lightbend.lagom.internal.javadsl.client.JavadslServiceClientImplementor
import com.lightbend.lagom.internal.javadsl.client.JavadslWebSocketClient
import com.lightbend.lagom.internal.javadsl.client.ServiceClientLoader
import com.lightbend.lagom.internal.registry.DevModeServiceDiscovery
import com.lightbend.lagom.internal.registry.ServiceRegistryClient
import com.lightbend.lagom.javadsl.api.Descriptor.Call
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.jackson.JacksonExceptionSerializer
import com.lightbend.lagom.javadsl.jackson.JacksonSerializerFactory
import play.api.inject.Binding
import play.api.inject.Module
import play.api.libs.ws.WSClient
import play.api.Configuration
import play.api.Environment
import play.api.Logger
import play.api.Mode

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ServiceRegistryModule(environment: Environment, configuration: Configuration) extends Module {
  private val logger = Logger(this.getClass)

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    if (environment.mode == Mode.Dev) {
      logger.debug {
        s"Running in ${environment.mode} mode. The ${classOf[ServiceLocator].getName} interface was " +
          "bound to an implementation that will query the embedded Service Locator. This is fine to use " +
          "only during development."
      }
      Seq(
        bind[ServiceLocatorConfig].toInstance(createDevServiceLocatorConfig),
        bind[ServiceRegistry].to(new ServiceRegistryProvider),
        bind[ServiceRegistryClient].to[JavaServiceRegistryClient],
        bind[ServiceLocator].to[ServiceRegistryServiceLocator],
        // This needs to be instantiated eagerly to ensure it initializes for
        // Akka libraries that use service discovery without dependency injection.
        bind[ServiceDiscovery].toProvider[DevModeSimpleServiceDiscoveryProvider].eagerly()
      )
    } else {
      logger.debug {
        s"Running in ${environment.mode} mode, hence Lagom is not binding the ${classOf[ServiceLocator].getName} " +
          "interface to a default concrete implementation as it's expected that the production " +
          "environment you are using provides a custom implementation of this interface."
      }
      Nil
    }
  }

  protected def createDevServiceLocatorConfig: ServiceLocatorConfig = {
    val serviceLocatorURLKey = "lagom.service-locator.url"
    val config               = configuration.underlying
    val url                  = config.getString(serviceLocatorURLKey)
    ServiceLocatorConfig(new URI(url))
  }
}

/**
 * This is needed to break the circular dependency between the ServiceRegistry and the ServiceLocator.
 */
@Singleton
class ServiceRegistryProvider extends Provider[ServiceRegistry] {
  @Inject private var config: ServiceLocatorConfig            = _
  @Inject private var ws: WSClient                            = _
  @Inject private var webSocketClient: JavadslWebSocketClient = _
  @Inject private var serviceInfo: ServiceInfo                = _
  @Inject private var environment: Environment                = _
  @Inject private var ec: ExecutionContext                    = _
  @Inject private var mat: Materializer                       = _

  @Inject private var jacksonSerializerFactory: JacksonSerializerFactory     = _
  @Inject private var jacksonExceptionSerializer: JacksonExceptionSerializer = _

  lazy val get = {
    val serviceLocator = new ClientServiceLocator(config)
    val implementor = new JavadslServiceClientImplementor(
      ws,
      webSocketClient,
      serviceInfo,
      serviceLocator,
      environment,
      NoTopicFactoryProvider
    )(ec, mat)
    val loader = new ServiceClientLoader(jacksonSerializerFactory, jacksonExceptionSerializer, environment, implementor)
    loader.loadServiceClient(classOf[ServiceRegistry])
  }

  /**
   * The service locator implementation used by the ServiceRegistry's client implementation.
   */
  private class ClientServiceLocator(config: ServiceLocatorConfig) extends BaseServiceLocator {
    protected override def lookup(name: String): Future[Optional[URI]] = {
      require(name == ServiceRegistry.SERVICE_NAME)
      Future.successful(Optional.of(config.url))
    }
  }
}

case class ServiceLocatorConfig(url: URI)

abstract class BaseServiceLocator extends ServiceLocator {
  import scala.compat.java8.FutureConverters._
  import scala.concurrent.ExecutionContext.Implicits.global

  override def locate(name: String, serviceCall: Call[_, _]): CompletionStage[Optional[URI]] = lookup(name).toJava

  override def doWithService[T](
      name: String,
      serviceCall: Call[_, _],
      block: JFunction[URI, CompletionStage[T]]
  ): CompletionStage[Optional[T]] = {
    val maybeLocation = lookup(name)
    maybeLocation
      .flatMap(maybeURI => {
        if (maybeURI.isPresent()) block.apply(maybeURI.get()).toScala.map(Optional.of(_))
        else Future.successful(Optional.empty[T])
      })
      .toJava
  }

  protected def lookup(name: String): Future[Optional[URI]]
}

@Singleton
private final class DevModeSimpleServiceDiscoveryProvider @Inject()(
    actorSystem: ActorSystem,
    serviceRegistryClient: ServiceRegistryClient
) extends Provider[DevModeServiceDiscovery] {

  override def get(): DevModeServiceDiscovery = {
    val discovery = DevModeServiceDiscovery(actorSystem)
    discovery.setServiceRegistryClient(serviceRegistryClient)
    discovery
  }

}
