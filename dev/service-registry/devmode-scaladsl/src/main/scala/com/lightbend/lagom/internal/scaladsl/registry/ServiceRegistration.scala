/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.registry

import java.net.URI

import akka.Done
import akka.actor.CoordinatedShutdown
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.typesafe.config.Config
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class ServiceRegistration(
  serviceInfo:         ServiceInfo,
  coordinatedShutdown: CoordinatedShutdown,
  config:              Config,
  registry:            ServiceRegistry
)(implicit ec: ExecutionContext) {

  // This code is similar to `ServerRegistrationModule` in project `registration-javadsl`
  // and `PlayRegisterWithServiceRegistry` in project `play-integration-javadsl

  private val logger: Logger = Logger(this.getClass)
  private val uri = {
    // In dev mode, `play.server.http.address` is used for both HTTP and HTTPS.
    // Reading one value or the other gets the same result.
    val httpAddress = config.getString("play.server.http.address")
    val httpsPort = config.getString("play.server.https.port")
    URI.create(s"https://$httpAddress:$httpsPort")
  }

  coordinatedShutdown.addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "unregister-services-from-service-locator-scaladsl") { () =>
    Future.sequence(serviceInfo.locatableServices.map {
      case (service, _) => registry.unregister(service).invoke()
    }).map(_ => Done)
  }

  serviceInfo.locatableServices.foreach {
    case (service, acls) =>
      registry.register(service)
        .invoke(ServiceRegistryService(uri, acls))
        .onComplete {
          case Success(_) =>
            logger.debug(s"Service name=[$service] successfully registered with service locator.")
          case Failure(e) =>
            logger.error(s"Service name=[$service}] couldn't register itself to the service locator.", e)
        }
  }

}
