/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.registry

import java.net.URI
import java.util.concurrent.CompletionStage
import java.util.{ Optional, List => JList }
import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.internal.registry.ServiceRegistryClient
import com.lightbend.lagom.javadsl.api.Descriptor.Call
import com.lightbend.lagom.javadsl.client.{ CircuitBreakersPanel, CircuitBreakingServiceLocator }

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext

@Singleton
private[lagom] class ServiceRegistryServiceLocator @Inject() (
  circuitBreakers: CircuitBreakersPanel,
  client:          ServiceRegistryClient,
  implicit val ec: ExecutionContext
) extends CircuitBreakingServiceLocator(circuitBreakers) {

  override def locateAll(name: String, serviceCall: Call[_, _]): CompletionStage[JList[URI]] =
    // a ServiceLocator doesn't know what a `portName` is so we fallback to `None` and the
    // implementation will return any registry without a name. For compatibility reasons,
    // any service regsitered using `http` as portName will also be registered without name.
    client.locateAll(name, None).map(_.asJava).toJava

  override def locate(name: String, serviceCall: Call[_, _]): CompletionStage[Optional[URI]] =
    // a ServiceLocator doesn't know what a `portName` is so we fallback to `None` and the
    // implementation will return any registry without a name. For compatibility reasons,
    // any service regsitered using `http` as portName will also be registered without name.
    client.locateAll(name, None).map(_.headOption.asJava).toJava

}
