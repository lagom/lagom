/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import java.net.{ URI, URISyntaxException }
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakers }
import com.lightbend.lagom.internal.scaladsl.client.{ CircuitBreakersConverter, CircuitBreakersPanelImpl }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
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
abstract class CircuitBreakingServiceLocator(circuitBreakers: CircuitBreakersPanel)(implicit ec: ExecutionContext) extends ServiceLocator {

  @deprecated(message = "Use constructor accepting {@link com.lightbend.lagom.scaladsl.client.CircuitBreakersPanel} instead", since = "1.4.0")
  def this(circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext) =
    // note we need a convert it so we can hit the new default constructor
    this(CircuitBreakersConverter.toScaladslCircuitBreakersPanel(circuitBreakers))(ec)

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
  def circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider

  lazy val circuitBreakerConfig: CircuitBreakerConfig = new CircuitBreakerConfig(configuration)

  // for backward compatibility we still need to provide it for wiring
  lazy val circuitBreakers: CircuitBreakers = {

    // only in case some 3rd party lib are still wiring the old one.
    actorSystem.log.warning(
      "CircuitBreakers is deprecated, use CircuitBreakersPanel instead. This warning is probably caused by your " +
        "service locator. If you are using a 3rd party service locator, upgrade your dependencies, otherwise this " +
        "service locator could become incompatible with Lagom in future versions."
    )

    new CircuitBreakers(actorSystem, circuitBreakerConfig, circuitBreakerMetricsProvider)
  }

  lazy val circuitBreakersPanel: CircuitBreakersPanel =
    new CircuitBreakersPanelImpl(actorSystem, circuitBreakerConfig, circuitBreakerMetricsProvider)
}

/**
 * Components for using the configuration service locator.
 */
trait ConfigurationServiceLocatorComponents extends CircuitBreakerComponents {
  lazy val serviceLocator: ServiceLocator = new ConfigurationServiceLocator(configuration, circuitBreakersPanel)(executionContext)
}

/**
 * A service locator that uses static configuration.
 */
class ConfigurationServiceLocator(configuration: Configuration, circuitBreakers: CircuitBreakersPanel)(implicit ec: ExecutionContext)
  extends CircuitBreakingServiceLocator(circuitBreakers) {

  @deprecated(message = "Use constructor accepting {@link com.lightbend.lagom.scaladsl.client.CircuitBreakersPanel} instead", since = "1.4.0")
  def this(configuration: Configuration, circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext) =
    // note we need a convert it so we can hit the new default constructor
    this(configuration, CircuitBreakersConverter.toScaladslCircuitBreakersPanel(circuitBreakers))(ec)

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

  lazy val serviceLocator: ServiceLocator = new StaticServiceLocator(staticServiceUri, circuitBreakersPanel)(executionContext)
}

/**
 * A static service locator, that always resolves the same URI.
 */
class StaticServiceLocator(uri: URI, circuitBreakers: CircuitBreakersPanel)(implicit ec: ExecutionContext) extends CircuitBreakingServiceLocator(circuitBreakers) {

  @deprecated(message = "Use constructor accepting {@link com.lightbend.lagom.scaladsl.CircuitBreakersPanel} instead", since = "1.4.0")
  def this(uri: URI, circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext) =
    // note we need a cast so we can hit the new default constructor
    this(uri, CircuitBreakersConverter.toScaladslCircuitBreakersPanel(circuitBreakers))(ec)

  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = Future.successful(Some(uri))
}

/**
 * Components for using the round robin service locator.
 */
trait RoundRobinServiceLocatorComponents extends CircuitBreakerComponents {
  def roundRobinServiceUris: immutable.Seq[URI]

  lazy val serviceLocator: ServiceLocator = new RoundRobinServiceLocator(roundRobinServiceUris, circuitBreakersPanel)(executionContext)
}

/**
 * A round robin service locator, that cycles through a list of URIs.
 */
class RoundRobinServiceLocator(uris: immutable.Seq[URI], circuitBreakers: CircuitBreakersPanel)(implicit ec: ExecutionContext) extends CircuitBreakingServiceLocator(circuitBreakers) {

  @deprecated(message = "Use constructor accepting {@link com.lightbend.lagom.scaladsl.client.CircuitBreakersPanel} instead", since = "1.4.0")
  def this(uris: immutable.Seq[URI], circuitBreakers: com.lightbend.lagom.internal.client.CircuitBreakers)(implicit ec: ExecutionContext) =
    // note we need a convert it so we can hit the new default constructor
    this(uris, CircuitBreakersConverter.toScaladslCircuitBreakersPanel(circuitBreakers))(ec)

  private val counter = new AtomicInteger(0)
  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
    val index = Math.abs(counter.getAndIncrement() % uris.size)
    val uri = uris(index)
    Future.successful(Some(uri))
  }
}
