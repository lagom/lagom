/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.registry

import java.net.URI

import com.lightbend.lagom.internal.registry.AbstractLoggingServiceRegistryClient
import com.lightbend.lagom.scaladsl.api.transport.NotFound

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

private[lagom] class ScalaServiceRegistryClient(registry: ServiceRegistry)(implicit ec: ExecutionContext)
  extends AbstractLoggingServiceRegistryClient {

  override protected def internalLocateAll(serviceName: String): Future[immutable.Seq[URI]] =
    registry.lookup(serviceName).invoke()
      .map(immutable.Seq[URI](_))
      .recover {
        case _: NotFound => Nil
      }

}
