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
import scala.collection.immutable

class ServiceRegistrationModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ServiceRegistrationModule.RegisterWithServiceRegistry].toSelf.eagerly(),
    bind[ServiceRegistrationModule.ServiceConfig].toProvider[ServiceRegistrationModule.ServiceConfigProvider]
  )
}

object ServiceRegistrationModule {

  class ServiceConfigProvider @Inject() (config: Config) extends Provider[ServiceConfig] {

    // This code is similar to `ServiceRegistration` in project `dev-mode-scala`
    // and `PlayRegisterWithServiceRegistry` in project `play-integration-javadsl
    override lazy val get = {
      // In dev mode, `play.server.http.address` is used for both HTTP and HTTPS.
      // Reading one value or the other gets the same result.
      val httpAddress = config.getString("play.server.http.address")

      val uris = immutable.Seq.newBuilder[URI]

      val httpPort = config.getString("play.server.http.port")
      uris += new URI(s"http://$httpAddress:$httpPort")

      if (config.hasPath("play.server.https.port")) {
        val httpsPort = config.getString("play.server.https.port")
        uris += new URI(s"https://$httpAddress:$httpsPort")
      }

      ServiceConfig(uris.result())
    }
  }

  case class ServiceConfig(uris: immutable.Seq[URI])

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
      val c = ServiceRegistryService.of(config.uris.asJava, service.descriptor.acls)
      registry.register(service.descriptor.name).invoke(c).exceptionally(new JFunction[Throwable, NotUsed] {
        def apply(t: Throwable) = {
          logger.error(s"Service name=[${service.descriptor.name}] couldn't register itself to the service locator.", t)
          NotUsed.getInstance()
        }
      })
    }
  }
}
