/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.registry

import java.net.URI
import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.internal.registry.AbstractLoggingServiceRegistryClient
import com.lightbend.lagom.javadsl.api.transport.NotFound

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
private[lagom] class JavaServiceRegistryClient @Inject() (
  registry:        ServiceRegistry,
  implicit val ec: ExecutionContext
) extends AbstractLoggingServiceRegistryClient {

  override protected def internalLocateAll(serviceName: String): Future[List[URI]] =
    registry.lookup(serviceName).invoke()
      .toScala
      .map(List[URI](_))
      .recover {
        case _: NotFound => Nil
      }

}
