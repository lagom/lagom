/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.registry

import java.net.URI

import com.lightbend.lagom.internal.registry.ServiceRegistryClient
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.client.{ CircuitBreakersPanel, CircuitBreakingServiceLocator }

import scala.concurrent.{ ExecutionContext, Future }

private[lagom] class ServiceRegistryServiceLocator(
  circuitBreakers: CircuitBreakersPanel,
  client:          ServiceRegistryClient,
  implicit val ec: ExecutionContext
) extends CircuitBreakingServiceLocator(circuitBreakers) {

  override def locateAll(name: String, serviceCall: Call[_, _]): Future[List[URI]] =
    client.locateAll(name)

  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] =
    locateAll(name, serviceCall).map(_.headOption)

}
