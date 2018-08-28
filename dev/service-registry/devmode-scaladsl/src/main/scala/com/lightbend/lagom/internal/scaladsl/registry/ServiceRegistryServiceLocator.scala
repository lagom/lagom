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
    // a ServiceLocator doesn't know what a `portName` is so we default to `None` and the
    // implementation will return any registry without a port name. This means that in order
    // for this queries to work any service registered using `http` as portName will also have
    // to be registered without name.
    client.locateAll(name, None).map(_.toList)

  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] =
    locateAll(name, serviceCall).map(_.headOption)

}
