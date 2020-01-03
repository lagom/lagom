/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.registry

import akka.Done
import akka.actor.CoordinatedShutdown
import com.lightbend.lagom.internal.registry.serviceDnsRecords
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.typesafe.config.Config
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class ServiceRegistration(
    serviceInfo: ServiceInfo,
    coordinatedShutdown: CoordinatedShutdown,
    config: Config,
    registry: ServiceRegistry
)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(this.getClass)

  private val uris = serviceDnsRecords(config)

  coordinatedShutdown.addTask(
    CoordinatedShutdown.PhaseBeforeServiceUnbind,
    "unregister-services-from-service-locator-scaladsl"
  ) { () =>
    Future
      .sequence(serviceInfo.locatableServices.map {
        case (service, _) => registry.unregister(service).invoke()
      })
      .map(_ => Done)
  }

  serviceInfo.locatableServices.foreach {
    case (service, acls) =>
      registry
        .register(service)
        .invoke(ServiceRegistryService(uris, acls))
        .onComplete {
          case Success(_) =>
            logger.debug(s"Service name=[$service] successfully registered with service locator.")
          case Failure(e) =>
            logger.error(s"Service name=[$service}] couldn't register itself to the service locator.", e)
        }
  }

}
