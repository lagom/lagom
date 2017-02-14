/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.lightbend.lagom.javadsl.api.Descriptor
import javax.inject.Inject
import javax.inject.Singleton

import com.lightbend.lagom.internal.client.CircuitBreakers
import com.lightbend.lagom.javadsl.client.CircuitBreakingServiceLocator

@Singleton
private[lagom] class TestServiceLocator @Inject() (
  circuitBreakers: CircuitBreakers,
  port:            TestServiceLocatorPort,
  implicit val ec: ExecutionContext
) extends CircuitBreakingServiceLocator(circuitBreakers) {

  private val futureUri = port.port.map(p => URI.create("http://localhost:" + p))

  override def locate(name: String, call: Descriptor.Call[_, _]): CompletionStage[Optional[URI]] =
    futureUri.map(uri => Optional.of(uri)).toJava
}

private[lagom] final case class TestServiceLocatorPort(port: Future[Int])
