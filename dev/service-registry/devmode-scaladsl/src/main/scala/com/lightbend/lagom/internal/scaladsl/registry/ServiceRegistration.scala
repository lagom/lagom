/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.registry

import java.net.URI

import akka.Done
import akka.actor.CoordinatedShutdown
import com.lightbend.lagom.scaladsl.api.ServiceAcl
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

  // In dev mode, `play.server.http.address` is used for both HTTP and HTTPS.
  // Reading one value or the other gets the same result.
  private val httpAddress = config.getString("play.server.http.address")
  private val uris = List("http", "https").map { scheme =>
    val port = config.getString(s"play.server.$scheme.port")
    new URI(s"$scheme://$httpAddress:$port")
  }

  coordinatedShutdown.addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "unregister-services-from-service-locator-scaladsl") { () =>
    Future.sequence(serviceInfo.locatableServices.map {
      case (service, _) => registry.unregister(service).invoke()
    }).map(_ => Done)
  }

  serviceInfo.locatableServices.foreach {
    case (service, acls) =>
      registry.register(service)
        .invoke(ServiceRegistryService(uris, acls))
        .onComplete {
          case Success(_) =>
            logger.debug(s"Service name=[$service] successfully registered with service locator.")
          case Failure(e) =>
            logger.error(s"Service name=[$service}] couldn't register itself to the service locator.", e)
        }
  }

}
