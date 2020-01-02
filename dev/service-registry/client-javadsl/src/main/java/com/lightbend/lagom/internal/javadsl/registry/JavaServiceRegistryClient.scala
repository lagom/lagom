/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.registry

import java.net.URI
import java.util.Optional

import javax.inject.Inject
import javax.inject.Singleton
import com.lightbend.lagom.internal.registry.AbstractLoggingServiceRegistryClient
import com.lightbend.lagom.javadsl.api.transport.NotFound

import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
private[lagom] class JavaServiceRegistryClient @Inject() (
    registry: ServiceRegistry,
    implicit val ec: ExecutionContext
) extends AbstractLoggingServiceRegistryClient {
  protected override def internalLocateAll(serviceName: String, portName: Option[String]): Future[immutable.Seq[URI]] =
    registry
      .lookup(serviceName, OptionConverters.toJava(portName))
      .invoke()
      .toScala
      .map(immutable.Seq[URI](_))
      .recover {
        case _: NotFound => Nil
      }
}
