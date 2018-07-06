/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.registry

import java.net.URI

import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

private[lagom] abstract class AbstractLoggingServiceRegistryClient(implicit ec: ExecutionContext)
  extends ServiceRegistryClient {

  protected val log: Logger = LoggerFactory.getLogger(getClass)

  override def locateAll(serviceName: String): Future[List[URI]] = {
    require(
      serviceName != ServiceRegistryClient.ServiceName,
      "The service registry client cannot locate the service registry service itself"
    )
    log.debug("Locating service name=[{}] ...", serviceName)

    val location: Future[List[URI]] = internalLocateAll(serviceName)

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

  protected def internalLocateAll(serviceName: String): Future[List[URI]]

}
