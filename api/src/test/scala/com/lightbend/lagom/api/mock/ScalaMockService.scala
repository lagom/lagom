/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.api.mock

import com.lightbend.lagom.javadsl.api.Descriptor
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.api.Service._
import com.lightbend.lagom.javadsl.api.ServiceCall
import com.lightbend.lagom.javadsl.api.transport.Method
import akka.NotUsed

trait ScalaMockService extends Service {

  def hello(): ServiceCall[String, NotUsed, String]

  override def descriptor(): Descriptor =
    named("/mock").`with`(restCall(Method.GET, "/hello/:name", hello()))
}

abstract class ScalaMockServiceWrong extends ScalaMockService
