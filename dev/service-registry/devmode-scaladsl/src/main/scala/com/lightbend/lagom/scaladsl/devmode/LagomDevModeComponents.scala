/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.devmode

import java.net.URI

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.discovery.ServiceDiscovery
import akka.stream.Materializer
import com.lightbend.lagom.devmode.internal.scaladsl.registry.ServiceRegistry
import com.lightbend.lagom.devmode.internal.registry.LagomDevModeServiceDiscovery
import com.lightbend.lagom.devmode.internal.registry.ServiceRegistryClient
import com.lightbend.lagom.internal.scaladsl.client.ScaladslServiceClient
import com.lightbend.lagom.internal.scaladsl.client.ScaladslServiceResolver
import com.lightbend.lagom.internal.scaladsl.client.ScaladslWebSocketClient
import com.lightbend.lagom.devmode.internal.scaladsl.registry._
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.client.CircuitBreakerComponents
import play.api.Environment
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
  def coordinatedShutdown: CoordinatedShutdown

  // Eagerly register services
  new ServiceRegistration(serviceInfo, coordinatedShutdown, config, serviceRegistry)(executionContext)
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
trait LagomDevModeServiceLocatorComponents extends CircuitBreakerComponents {

  /**
   * If being used in a Lagom service, this will be implemented by
   * [[com.lightbend.lagom.scaladsl.server.LagomServerComponents]], however if it's being
   * used by a Play application, this will need to be provided manually.
   */
  def serviceInfo: ServiceInfo
  def wsClient: WSClient
  def scaladslWebSocketClient: ScaladslWebSocketClient
  def environment: Environment
  def executionContext: ExecutionContext
  def materializer: Materializer
  def actorSystem: ActorSystem

  lazy val devModeServiceLocatorUrl: URI = URI.create(config.getString("lagom.service-locator.url"))
  lazy val serviceRegistry: ServiceRegistry = {
    // We need to create our own static service locator since the service locator will depend on this service registry.
    val staticServiceLocator = new ServiceLocator {
      override def doWithService[T](name: String, serviceCall: Call[_, _])(
          block: (URI) => Future[T]
      )(implicit ec: ExecutionContext): Future[Option[T]] = {
        if (name == ServiceRegistryClient.ServiceName) {
          block(devModeServiceLocatorUrl).map(Some.apply)
        } else {
          Future.successful(None)
        }
      }
      override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
        if (name == ServiceRegistryClient.ServiceName) {
          Future.successful(Some(devModeServiceLocatorUrl))
        } else {
          Future.successful(None)
        }
      }
    }

    val serviceClient = new ScaladslServiceClient(
      wsClient,
      scaladslWebSocketClient,
      serviceInfo,
      staticServiceLocator,
      new ScaladslServiceResolver(new DefaultExceptionSerializer(environment)),
      None
    )(executionContext, materializer)

    serviceClient.implement[ServiceRegistry]
  }

  private lazy val serviceRegistryClient: ServiceRegistryClient =
    new ScalaServiceRegistryClient(serviceRegistry)(executionContext)

  private lazy val devModeSimpleServiceDiscovery: LagomDevModeServiceDiscovery =
    LagomDevModeServiceDiscovery(actorSystem)
  // This needs to be done eagerly to ensure it initializes for Akka libraries
  // that use service discovery without dependency injection.
  devModeSimpleServiceDiscovery.setServiceRegistryClient(serviceRegistryClient)

  // Make ServiceLocator and SimpleServiceDiscovery available to the application
  lazy val serviceLocator: ServiceLocator =
    new ServiceRegistryServiceLocator(circuitBreakersPanel, serviceRegistryClient, executionContext)
  lazy val serviceDiscovery: ServiceDiscovery =
    devModeSimpleServiceDiscovery
}
