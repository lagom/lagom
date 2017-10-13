/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.Service.{ pathCall, named, restCall }
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import play.api.{ Environment, Mode }

object PathProvider {
  val PATH = "/some-path"
}

/**
 * A simple service tests may implement to provide their needed behavior.
 */
trait SimpleStrictService extends Service {
  override def descriptor: Descriptor =
    named("simple-strict")
      .withCalls(restCall(Method.GET, PathProvider.PATH, simpleGet _))
      .withExceptionSerializer(new DefaultExceptionSerializer(Environment.simple(mode = Mode.Dev)))

  def simpleGet(): ServiceCall[NotUsed, String]
}

/**
 * A simple service that uses Lagom's HeaderFilters. Tests may implement this to provide their needed behavior.
 */
abstract class FilteredStrictService(atomicInteger: AtomicInteger) extends SimpleStrictService {
  override def descriptor: Descriptor =
    super.descriptor.withHeaderFilter(new VerboseHeaderLagomFilter(atomicInteger))

}

/**
 * A simple service tests may implement to provide their needed behavior.
 */
trait SimpleStreamedService extends Service {
  override def descriptor: Descriptor =
    named("simple-streamed")
      .withCalls(pathCall(PathProvider.PATH, streamed _))
      .withExceptionSerializer(new DefaultExceptionSerializer(Environment.simple(mode = Mode.Dev)))

  def streamed(): ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]
}
