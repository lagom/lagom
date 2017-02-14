/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.registry

import java.net.URI

import com.lightbend.lagom.internal.client.CircuitBreakers
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.transport.NotFound
import com.lightbend.lagom.scaladsl.client.CircuitBreakingServiceLocator
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class ServiceRegistryServiceLocator(
  circuitBreakers: CircuitBreakers,
  registry:        ServiceRegistry,
  implicit val ec: ExecutionContext
) extends CircuitBreakingServiceLocator(circuitBreakers) {

  private val logger: Logger = Logger(this.getClass())

  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
    require(name != ServiceRegistry.ServiceName)
    logger.debug(s"Locating service name=[$name] ...")

    val location: Future[Option[URI]] = {
      registry.lookup(name).invoke().map(Some.apply).recover {
        case notFound: NotFound => None
      }
    }
    location.onComplete {
      case Success(Some(address)) =>
        logger.debug(s"Service name=[$name] can be reached at address=[${address.getPath}]")
      case Success(None) =>
        logger.warn(s"Service name=[$name] was not found. Hint: Maybe it was not registered?")
      case Failure(e) =>
        logger.warn(s"The service locator replied with an error when looking up the service name=[$name] address", e)
    }
    location
  }
}
