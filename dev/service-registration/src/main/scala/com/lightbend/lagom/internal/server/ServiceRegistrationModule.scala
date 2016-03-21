/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import java.util.function.{ Function => JFunction }

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.registry.ServiceRegistry
import com.lightbend.lagom.internal.registry.ServiceRegistryService
import com.lightbend.lagom.internal.server.ServiceRegistrationModule.RegisterWithServiceRegistry

import akka.NotUsed
import javax.inject.Inject
import javax.inject.Singleton
import play.api.Logger
import play.api.inject.ApplicationLifecycle

class ServiceRegistrationModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[ServiceRegistrationModule.RegisterWithServiceRegistry]).asEagerSingleton()
  }
}

object ServiceRegistrationModule {
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
        registry.unregister().invoke(service.descriptor.name, NotUsed.getInstance).toScala
      }).map(_ => ())
    }

    locatableServices.foreach { service =>
      registry.register().invoke(service.descriptor.name, new ServiceRegistryService(config.url, service.descriptor.acls)).exceptionally(new JFunction[Throwable, NotUsed] {
        def apply(t: Throwable) = {
          logger.error(s"Service name=[${service.descriptor.name}] couldn't register itself to the service locator.", t)
          NotUsed.getInstance()
        }
      })
    }
  }
}
