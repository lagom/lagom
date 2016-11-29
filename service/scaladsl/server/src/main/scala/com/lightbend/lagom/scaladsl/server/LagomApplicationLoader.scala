/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakerMetricsProviderImpl, CircuitBreakers }
import com.lightbend.lagom.internal.scaladsl.client.{ ScaladslServiceClient, ScaladslServiceResolver, ScaladslWebSocketClient }
import com.lightbend.lagom.internal.scaladsl.registry.{ ServiceRegistration, ServiceRegistry, ServiceRegistryServiceLocator }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.{ ServiceInfo, ServiceLocator }
import com.lightbend.lagom.scaladsl.client.{ CircuitBreakingServiceLocator, LagomServiceClientComponents }
import play.api._
import play.api.ApplicationLoader.Context
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import play.core.DefaultWebCommands

import scala.concurrent.{ ExecutionContext, Future }

abstract class LagomApplicationLoader extends ApplicationLoader {
  override final def load(context: Context): Application = context.environment.mode match {
    case Mode.Dev => loadDevMode(LagomApplicationContext(context)).application
    case _        => load(LagomApplicationContext(context)).application
  }

  def load(context: LagomApplicationContext): LagomApplication
  def loadDevMode(context: LagomApplicationContext): LagomApplication = load(context)
}

sealed trait LagomApplicationContext {
  val playContext: Context
}

object LagomApplicationContext {
  def apply(context: Context): LagomApplicationContext = new LagomApplicationContext {
    override val playContext: Context = context
  }
  def Test = apply(Context(Environment.simple(), None, new DefaultWebCommands, Configuration.empty))
}

abstract class LagomApplication(context: LagomApplicationContext)
  extends BuiltInComponentsFromContext(context.playContext)
  with LagomServerComponents
  with LagomServiceClientComponents {

  override implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
  override lazy val configuration: Configuration = Configuration.load(environment) ++
    context.playContext.initialConfiguration ++ additionalConfiguration
  def additionalConfiguration: Configuration = Configuration.empty
}

trait LocalServiceLocator {
  def lagomServicePort: Future[Int]
  def lagomServer: LagomServer
  def actorSystem: ActorSystem
  def executionContext: ExecutionContext
  def configuration: Configuration

  lazy val circuitBreakerConfig: CircuitBreakerConfig = new CircuitBreakerConfig(configuration)
  lazy val circuitBreakers = new CircuitBreakers(actorSystem, circuitBreakerConfig, new CircuitBreakerMetricsProviderImpl(actorSystem))
  lazy val serviceLocator: ServiceLocator = new CircuitBreakingServiceLocator(circuitBreakers)(executionContext) {
    val services = lagomServer.serviceBindings.map(_.descriptor.name).toSet

    def getUri(name: String): Future[Option[URI]] = lagomServicePort.map {
      case port if services(name) => Some(URI.create(s"http://localhost:$port"))
      case _                      => None
    }(executionContext)

    override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] =
      getUri(name)
  }
}

trait LagomDevModeComponents {
  def wsClient: WSClient
  def scaladslWebSocketClient: ScaladslWebSocketClient
  def environment: Environment
  def configuration: Configuration
  def executionContext: ExecutionContext
  def materializer: Materializer
  def serviceInfo: ServiceInfo
  def actorSystem: ActorSystem
  def applicationLifecycle: ApplicationLifecycle

  lazy val circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider = new CircuitBreakerMetricsProviderImpl(actorSystem)
  lazy val circuitBreakerConfig: CircuitBreakerConfig = new CircuitBreakerConfig(configuration)
  lazy val circuitBreakers: CircuitBreakers = new CircuitBreakers(actorSystem, circuitBreakerConfig, circuitBreakerMetricsProvider)

  lazy val serviceRegistry: ServiceRegistry = {
    val staticServiceLocator = new ServiceLocator {
      val serviceLocatorUrl = URI.create(configuration.underlying.getString("lagom.service-locator.url"))
      override def doWithService[T](name: String, serviceCall: Call[_, _])(block: (URI) => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
        if (name == ServiceRegistry.ServiceName) {
          block(serviceLocatorUrl).map(Some.apply)
        } else {
          Future.successful(None)
        }
      }
      override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
        if (name == ServiceRegistry.ServiceName) {
          Future.successful(Some(serviceLocatorUrl))
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

  // Eagerly register services
  new ServiceRegistration(serviceInfo, applicationLifecycle, configuration, serviceRegistry)(executionContext)
}
