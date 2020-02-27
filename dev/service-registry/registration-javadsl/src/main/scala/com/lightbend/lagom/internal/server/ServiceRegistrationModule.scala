/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.server

import java.net.URI
import java.util.function.{ Function => JFunction }

import akka.actor.CoordinatedShutdown
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.lightbend.lagom.devmode.internal.registry.registry.serviceDnsRecords
import com.typesafe.config.Config
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import play.api.inject.Binding
import play.api.inject.Module
import play.api.Configuration
import play.api.Environment
import play.api.Logger

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.collection.immutable

class ServiceRegistrationModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ServiceRegistrationModule.RegisterWithServiceRegistry].toSelf.eagerly(),
    bind[ServiceRegistrationModule.ServiceConfig].toProvider[ServiceRegistrationModule.ServiceConfigProvider]
  )
}

object ServiceRegistrationModule {
  class ServiceConfigProvider @Inject() (config: Config) extends Provider[ServiceConfig] {
    override lazy val get = ServiceConfig(serviceDnsRecords(config))
  }

  case class ServiceConfig(uris: immutable.Seq[URI])

  /**
   * Automatically registers the service on start, and also unregister it on stop.
   */
  @Singleton
  private class RegisterWithServiceRegistry @Inject() (
      coordinatedShutdown: CoordinatedShutdown,
      resolvedServices: ResolvedServices,
      config: ServiceConfig,
      registry: ServiceRegistry
  )(implicit ec: ExecutionContext) {
    private lazy val logger: Logger = Logger(this.getClass())

    private val locatableServices = resolvedServices.services.filter(_.descriptor.locatableService)

    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      "unregister-services-from-service-locator-javadsl"
    ) { () =>
      Future
        .sequence(locatableServices.map { service =>
          registry.unregister(service.descriptor.name).invoke().toScala
        })
        .map(_ => Done)
    }

    locatableServices.foreach { service =>
      val c = ServiceRegistryService.of(config.uris.asJava, service.descriptor.acls)
      registry
        .register(service.descriptor.name)
        .invoke(c)
        .exceptionally(new JFunction[Throwable, NotUsed] {
          def apply(t: Throwable) = {
            logger
              .error(s"Service name=[${service.descriptor.name}] couldn't register itself to the service locator.", t)
            NotUsed.getInstance()
          }
        })
    }
  }
}
