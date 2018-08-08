/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.server

import java.net.URI
import java.util.function.{ Function => JFunction }

import akka.actor.CoordinatedShutdown
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.javadsl.registry.{ ServiceRegistry, ServiceRegistryService }
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.typesafe.config.Config
import javax.inject.{ Inject, Provider, Singleton }
import play.api.inject.{ Binding, Module }
import play.api.{ Configuration, Environment, Logger }

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._

class ServiceRegistrationModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ServiceRegistrationModule.RegisterWithServiceRegistry].toSelf.eagerly(),
    bind[ServiceRegistrationModule.ServiceConfig].toProvider[ServiceRegistrationModule.ServiceConfigProvider]
  )
}

object ServiceRegistrationModule {

  class ServiceConfigProvider @Inject() (config: Config) extends Provider[ServiceConfig] {

    override lazy val get = {
      val httpAddress = config.getString("play.server.http.address")
      val httpPort = config.getString("play.server.http.port")
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
    coordinatedShutdown: CoordinatedShutdown,
    resolvedServices:    ResolvedServices,
    config:              ServiceConfig,
    registry:            ServiceRegistry
  )(implicit ec: ExecutionContext) {

    private lazy val logger: Logger = Logger(this.getClass())

    private val locatableServices = resolvedServices.services.filter(_.descriptor.locatableService)

    coordinatedShutdown.addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "unregister-services-from-service-locator-javadsl") { () =>
      Future.sequence(locatableServices.map { service =>
        registry.unregister(service.descriptor.name).invoke().toScala
      }).map(_ => Done)
    }

    locatableServices.foreach { service =>
      val uris = Seq(config.url)
      val c = ServiceRegistryService.of(uris.asJava, service.descriptor.acls)
      registry.register(service.descriptor.name).invoke(c).exceptionally(new JFunction[Throwable, NotUsed] {
        def apply(t: Throwable) = {
          logger.error(s"Service name=[${service.descriptor.name}] couldn't register itself to the service locator.", t)
          NotUsed.getInstance()
        }
      })
    }
  }
}
