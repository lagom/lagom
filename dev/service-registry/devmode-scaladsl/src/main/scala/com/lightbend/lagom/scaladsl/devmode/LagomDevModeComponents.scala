/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.devmode

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakerMetricsProviderImpl, CircuitBreakers }
import com.lightbend.lagom.internal.scaladsl.client.{ ScaladslServiceClient, ScaladslServiceResolver, ScaladslWebSocketClient }
import com.lightbend.lagom.internal.scaladsl.registry.{ ServiceRegistration, ServiceRegistry, ServiceRegistryServiceLocator }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.{ ServiceInfo, ServiceLocator }
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import play.api.{ Configuration, Environment }
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Provides the Lagom dev mode components.
 *
 * This trait primarily has two responsibilities, it provides a service locator that uses Lagom's development service
 * locator, and it registers any services returned by the `serviceInfo` component with the Lagom develompent service
 * registry.
 *
 * It can be used both by Lagom services, and also by non Lagom services, such as pure Play applications, in order to
 * use the Lagom dev mode service locator and register components with Lagom. When used with non Lagom applications,
 * `serviceInfo` will need to manually be implemented to return the service name and any ACLs for the service gateway
 * to use.
 *
 * It expects the service locator URL to be provided using the `lagom.service-locator.url` property, which by default
 * will be automatically provided to the service by Lagom's dev mode build plugins.
 */
trait LagomDevModeComponents extends LagomDevModeServiceLocatorComponents {
  def applicationLifecycle: ApplicationLifecycle

  // Eagerly register services
  new ServiceRegistration(serviceInfo, applicationLifecycle, configuration, serviceRegistry)(executionContext)
}

/**
 * Provides the Lagom dev mode service locator.
 *
 * It can be used both by Lagom services, and also by non Lagom services, such as pure Play applications, in order to
 * use the Lagom dev mode service locator. When used with non Lagom applications, `serviceInfo` will need to manually
 * be implemented to return the service name.
 *
 * It expects the service locator URL to be provided using the `lagom.service-locator.url` property, which by default
 * will be automatically provided to the service by Lagom's dev mode build plugins.
 */
trait LagomDevModeServiceLocatorComponents {
  /**
   * If being used in a Lagom service, this will be implemented by
   * [[com.lightbend.lagom.scaladsl.server.LagomServerComponents]], however if it's being
   * used by a Play application, this will need to be provided manually.
   */
  def serviceInfo: ServiceInfo
  def wsClient: WSClient
  def scaladslWebSocketClient: ScaladslWebSocketClient
  def environment: Environment
  def configuration: Configuration
  def executionContext: ExecutionContext
  def materializer: Materializer
  def actorSystem: ActorSystem

  lazy val circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider = new CircuitBreakerMetricsProviderImpl(actorSystem)
  lazy val circuitBreakerConfig: CircuitBreakerConfig = new CircuitBreakerConfig(configuration)
  lazy val circuitBreakers: CircuitBreakers = new CircuitBreakers(actorSystem, circuitBreakerConfig, circuitBreakerMetricsProvider)

  lazy val devModeServiceLocatorUrl: URI = URI.create(configuration.underlying.getString("lagom.service-locator.url"))
  lazy val serviceRegistry: ServiceRegistry = {

    // We need to create our own static service locator since the service locator will depend on this service registry.
    val staticServiceLocator = new ServiceLocator {
      override def doWithService[T](name: String, serviceCall: Call[_, _])(block: (URI) => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
        if (name == ServiceRegistry.ServiceName) {
          block(devModeServiceLocatorUrl).map(Some.apply)
        } else {
          Future.successful(None)
        }
      }
      override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
        if (name == ServiceRegistry.ServiceName) {
          Future.successful(Some(devModeServiceLocatorUrl))
        } else {
          Future.successful(None)
        }
      }
    }

    val serviceClient = new ScaladslServiceClient(wsClient, scaladslWebSocketClient, serviceInfo, staticServiceLocator,
      new ScaladslServiceResolver(new DefaultExceptionSerializer(environment)), None)(executionContext, materializer)

    serviceClient.implement[ServiceRegistry]
  }

  lazy val serviceLocator: ServiceLocator = new ServiceRegistryServiceLocator(circuitBreakers, serviceRegistry, executionContext)
}
