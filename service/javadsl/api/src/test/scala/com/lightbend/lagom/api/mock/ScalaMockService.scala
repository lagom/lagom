/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.mock

import java.util.UUID

import com.lightbend.lagom.javadsl.api.Descriptor
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.api.ScalaService._
import com.lightbend.lagom.javadsl.api.ServiceCall
import com.lightbend.lagom.javadsl.api.transport.Method

trait ScalaMockService extends Service {

  def hello(): ServiceCall[UUID, String]

  override def descriptor(): Descriptor =
    named("/mock").withCalls(restCall(Method.GET, "/hello/:name", hello _))
}

abstract class ScalaMockServiceWrong extends ScalaMockService
