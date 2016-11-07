/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import java.net.URI

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakerMetricsProviderImpl, CircuitBreakers }
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.client.{ CircuitBreakingServiceLocator, LagomServiceClientComponents }
import play.api._
import play.api.ApplicationLoader.Context
import play.core.DefaultWebCommands

import scala.concurrent.{ ExecutionContext, Future }

abstract class LagomApplicationLoader extends ApplicationLoader {
  override final def load(context: Context): Application =
    load(LagomApplicationContext(context)).application

  def load(context: LagomApplicationContext): LagomApplication
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
  override lazy val configuration: Configuration = Configuration.load(environment) ++ context.playContext.initialConfiguration
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
