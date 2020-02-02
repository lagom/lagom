/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.registry

import akka.Done
import akka.actor.CoordinatedShutdown
import com.lightbend.lagom.internal.registry.serviceDnsRecords
import com.lightbend.lagom.devmode.internal.scaladsl.registry.ServiceRegistry
import com.lightbend.lagom.devmode.internal.scaladsl.registry.ServiceRegistryService
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.typesafe.config.Config
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

import scala.collection._

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
    registry.unregister(serviceInfo.serviceName).invoke().map(_ => Done)
  }

  registry
    .register(serviceInfo.serviceName)
    .invoke(new ServiceRegistryService(uris, immutable.Seq(serviceInfo.acls.toSeq: _*)))
    .onComplete {
      case Success(_) =>
        logger.debug(s"Service name=[${serviceInfo.serviceName}] successfully registered with service locator.")
      case Failure(e) =>
        logger.error(s"Service name=[${serviceInfo.serviceName}] couldn't register itself to the service locator.", e)
    }
}
