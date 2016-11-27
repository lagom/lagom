/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import java.net.URI
import java.util.function.{ Function => JFunction }

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.google.inject.Provider
import akka.NotUsed
import javax.inject.Inject
import javax.inject.Singleton

import com.lightbend.lagom.internal.javadsl.registry.{ ServiceRegistry, ServiceRegistryService }
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import play.api.Configuration
import play.api.Environment
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.inject.Binding
import play.api.inject.Module

class ServiceRegistrationModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ServiceRegistrationModule.RegisterWithServiceRegistry].toSelf.eagerly(),
    bind[ServiceRegistrationModule.ServiceConfig].toProvider[ServiceRegistrationModule.ServiceConfigProvider]
  )
}

object ServiceRegistrationModule {

  class ServiceConfigProvider @Inject() (config: Configuration) extends Provider[ServiceConfig] {
    override lazy val get = {
      val httpAddress = config.underlying.getString("play.server.http.address")
      val httpPort = config.getString("play.server.http.port").get
      val url = new URI(s"http://$httpAddress:$httpPort")

      ServiceConfig(url)
    }
  }

  case class ServiceConfig(url: URI)

  /**
   * Automatically registers the service on start, and also unregister it on stop.
   */
  @Singleton
  private class RegisterWithServiceRegistry @Inject() (
    lifecycle:        ApplicationLifecycle,
    resolvedServices: ResolvedServices,
    config:           ServiceConfig,
    registry:         ServiceRegistry
  )(implicit ec: ExecutionContext) {

    private lazy val logger: Logger = Logger(this.getClass())

    private val locatableServices = resolvedServices.services.filter(_.descriptor.locatableService)

    lifecycle.addStopHook { () =>
      Future.sequence(locatableServices.map { service =>
        registry.unregister(service.descriptor.name).invoke().toScala
      }).map(_ => ())
    }

    locatableServices.foreach { service =>
      registry.register(service.descriptor.name).invoke(new ServiceRegistryService(config.url, service.descriptor.acls)).exceptionally(new JFunction[Throwable, NotUsed] {
        def apply(t: Throwable) = {
          logger.error(s"Service name=[${service.descriptor.name}] couldn't register itself to the service locator.", t)
          NotUsed.getInstance()
        }
      })
    }
  }
}
