/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.registry

import java.net.URI

import scala.concurrent.Future

private[lagom] trait ServiceRegistryClient {
  def locateAll(serviceName: String): Future[List[URI]]
}

private[lagom] object ServiceRegistryClient {
  final val ServiceName = "lagom-service-registry"
}
