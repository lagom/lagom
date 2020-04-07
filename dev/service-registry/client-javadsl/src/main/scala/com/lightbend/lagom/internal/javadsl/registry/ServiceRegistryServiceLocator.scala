/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.registry

import java.net.URI
import java.util.concurrent.CompletionStage
import java.util.Optional
import java.util.{ List => JList }
import javax.inject.Inject
import javax.inject.Singleton

import com.lightbend.lagom.internal.registry.ServiceRegistryClient
import com.lightbend.lagom.javadsl.api.Descriptor.Call
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel
import com.lightbend.lagom.javadsl.client.CircuitBreakingServiceLocator

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext

@Singleton
private[lagom] class ServiceRegistryServiceLocator @Inject() (
    circuitBreakers: CircuitBreakersPanel,
    client: ServiceRegistryClient,
    implicit val ec: ExecutionContext
) extends CircuitBreakingServiceLocator(circuitBreakers) {
  override def locateAll(name: String, serviceCall: Call[_, _]): CompletionStage[JList[URI]] =
    // a ServiceLocator doesn't know what a `portName` is so we default to `None` and the
    // implementation will return any registry without a port name. This means that in order
    // for this queries to work any service registered using `http` as portName will also have
    // to be registered without name.
    client.locateAll(name, None).map(_.asJava).toJava

  override def locate(name: String, serviceCall: Call[_, _]): CompletionStage[Optional[URI]] =
    // a ServiceLocator doesn't know what a `portName` is so we default to `None` and the
    // implementation will return any registry without a port name. This means that in order
    // for this queries to work any service registered using `http` as portName will also have
    // to be registered without name.
    client.locateAll(name, None).map(_.headOption.asJava).toJava
}
