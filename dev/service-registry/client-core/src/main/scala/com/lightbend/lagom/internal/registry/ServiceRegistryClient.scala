/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.registry

import java.net.URI

import scala.collection.immutable
import scala.concurrent.Future

private[lagom] trait ServiceRegistryClient {
  // TODO: add support for `protocol:Option[String]` on lookup
  def locateAll(serviceName: String, portName: Option[String]): Future[immutable.Seq[URI]]
}

private[lagom] object ServiceRegistryClient {
  final val ServiceName = "lagom-service-registry"
}
