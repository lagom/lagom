/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import java.util.function.{ Function => JFunction }
import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.internal.registry.{ ServiceRegistryModule, ServiceRegistry, ServiceRegistryService }
import akka.NotUsed
import play.api.{ Mode, Environment, Configuration, Logger }
import play.api.inject.{ Injector, ApplicationLifecycle }

import scala.concurrent.Future
import scala.compat.java8.FutureConverters._

/**
 * Automatically registers the service on start, and also unregister it on stop.
 */
@Singleton
class RegisterWithServiceRegistry @Inject() (injector: Injector, configuration: Configuration, environment: Environment,
                                             lifecycle: ApplicationLifecycle, resolvedServices: ResolvedServices, config: ServiceConfig) {
  private lazy val logger: Logger = Logger(this.getClass())

  import play.api.libs.iteratee.Execution.Implicits.trampoline

  // Only bind with the service registry if it's enabled
  if (ServiceRegistryModule.isServiceRegistryServiceLocatorEnabled(configuration) && environment.mode == Mode.Dev) {
    val registry = injector.instanceOf[ServiceRegistry]

    val locatableServices = resolvedServices.services.filter(_.descriptor.locatableService)

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
