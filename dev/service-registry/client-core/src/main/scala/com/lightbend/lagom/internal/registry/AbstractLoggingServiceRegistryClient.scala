/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.registry

import java.net.URI

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

private[lagom] abstract class AbstractLoggingServiceRegistryClient(implicit ec: ExecutionContext)
    extends ServiceRegistryClient {
  protected val log: Logger = LoggerFactory.getLogger(getClass)

  override def locateAll(serviceName: String, portName: Option[String]): Future[immutable.Seq[URI]] = {
    require(
      serviceName != ServiceRegistryClient.ServiceName,
      "The service registry client cannot locate the service registry service itself"
    )
    log.debug("Locating service name=[{}] ...", serviceName)

    val location: Future[immutable.Seq[URI]] = internalLocateAll(serviceName, portName)

    location.onComplete {
      case Success(Nil) =>
        log.warn("serviceName=[{}] was not found. Hint: Maybe it was not started?", serviceName)
      case Success(uris) =>
        log.debug("serviceName=[{}] can be reached at uris=[{}]", serviceName: Any, uris: Any)
      case Failure(e) =>
        log.warn("Service registry replied with an error when looking up serviceName=[{}]", serviceName: Any, e: Any)
    }

    location
  }

  protected def internalLocateAll(serviceName: String, portName: Option[String]): Future[immutable.Seq[URI]]
}
