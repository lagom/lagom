/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import java.net.{ URI, URISyntaxException }
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakerMetricsProviderImpl, CircuitBreakers }
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.{ CircuitBreaker, Descriptor, ServiceLocator }
import com.typesafe.config.ConfigException
import play.api.Configuration

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Abstract service locator that provides circuit breaking.
 *
 * Generally, only the [[ServiceLocator.locate()]] method needs to be implemented, however
 * [[doWithServiceImpl()]] can be overridden if the service locator wants to
 * handle failures in some way.
 */
abstract class CircuitBreakingServiceLocator(circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext) extends ServiceLocator {

  /**
   * Do the given block with the given service looked up.
   *
   * This is invoked by [[doWithService()]], after wrapping the passed in block
   * in a circuit breaker if configured to do so.
   *
   * The default implementation just delegates to the [[locate()]] method, but this method
   * can be overridden if the service locator wants to inject other behaviour after the service call is complete.
   *
   * @param name        The service name.
   * @param serviceCall The service call that needs the service lookup.
   * @param block       A block of code that will use the looked up service, typically, to make a call on that service.
   * @return A future of the result of the block, if the service lookup was successful.
   */
  protected def doWithServiceImpl[T](name: String, serviceCall: Descriptor.Call[_, _])(block: URI => Future[T]): Future[Option[T]] = {
    locate(name, serviceCall).flatMap {
      case (Some(uri)) => block(uri).map(Some.apply)
      case None        => Future.successful(None)
    }
  }

  override final def doWithService[T](name: String, serviceCall: Call[_, _])(block: (URI) => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
    serviceCall.circuitBreaker.filter(_ != CircuitBreaker.None).map { cb =>
      val circuitBreakerId = cb match {
        case cbid: CircuitBreaker.CircuitBreakerId => cbid.id
        case _                                     => name
      }

      doWithServiceImpl(name, serviceCall) { uri =>
        circuitBreakers.withCircuitBreaker(circuitBreakerId)(block(uri))
      }
    }.getOrElse {
      doWithServiceImpl(name, serviceCall)(block)
    }
  }
}

/**
 * Components required for circuit breakers.
 */
trait CircuitBreakerComponents {
  def actorSystem: ActorSystem
  def configuration: Configuration
  def executionContext: ExecutionContext

  lazy val circuitBreakerConfig: CircuitBreakerConfig = new CircuitBreakerConfig(configuration)
  lazy val circuitBreakers = new CircuitBreakers(actorSystem, circuitBreakerConfig, new CircuitBreakerMetricsProviderImpl(actorSystem))
}

/**
 * Components for using the configuration service locator.
 */
trait ConfigurationServiceLocatorComponents extends CircuitBreakerComponents {
  lazy val serviceLocator: ServiceLocator = new ConfigurationServiceLocator(configuration, circuitBreakers)(executionContext)
}

/**
 * A service locator that uses static configuration.
 */
class ConfigurationServiceLocator(configuration: Configuration, circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext)
  extends CircuitBreakingServiceLocator(circuitBreakers) {

  private val LagomServicesKey: String = "lagom.services"

  private val services = {
    if (configuration.underlying.hasPath(LagomServicesKey)) {
      val config = configuration.underlying.getConfig(LagomServicesKey)
      import scala.collection.JavaConverters._
      (for {
        key <- config.root.keySet.asScala
      } yield {
        try {
          key -> URI.create(config.getString(key))
        } catch {
          case e: ConfigException.WrongType =>
            throw new IllegalStateException(s"Error loading configuration for ConfigurationServiceLocator. Expected lagom.services.$key to be a String, but was ${config.getValue(key).valueType}", e)
          case e: URISyntaxException =>
            throw new IllegalStateException(s"Error loading configuration for ConfigurationServiceLocator. Expected lagom.services.$key to be a URI, but it failed to parse", e)
        }
      }).toMap
    } else {
      Map.empty[String, URI]
    }
  }

  override def locate(name: String, serviceCall: Call[_, _]) = {
    Future.successful(services.get(name))
  }
}

/**
 * Components for using the static service locator.
 */
trait StaticServiceLocatorComponents extends CircuitBreakerComponents {
  def staticServiceUri: URI

  lazy val serviceLocator: ServiceLocator = new StaticServiceLocator(staticServiceUri, circuitBreakers)(executionContext)
}

/**
 * A static service locator, that always resolves the same URI.
 */
class StaticServiceLocator(uri: URI, circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext) extends CircuitBreakingServiceLocator(circuitBreakers) {
  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = Future.successful(Some(uri))
}

/**
 * Components for using the round robin service locator.
 */
trait RoundRobinServiceLocatorComponents extends CircuitBreakerComponents {
  def roundRobinServiceUris: immutable.Seq[URI]

  lazy val serviceLocator: ServiceLocator = new RoundRobinServiceLocator(roundRobinServiceUris, circuitBreakers)(executionContext)
}

/**
 * A round robin service locator, that cycles through a list of URIs.
 */
class RoundRobinServiceLocator(uris: immutable.Seq[URI], circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext) extends CircuitBreakingServiceLocator(circuitBreakers) {
  private val counter = new AtomicInteger(0)
  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
    val index = Math.abs(counter.getAndIncrement() % uris.size)
    val uri = uris(index)
    Future.successful(Some(uri))
  }
}
