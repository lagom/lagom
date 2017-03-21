/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.tools.tests.scaladsl

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.Service._

import scala.concurrent.Future

trait NoAclService extends Service {
  def getMock(id: String): ServiceCall[NotUsed, NotUsed]

  def descriptor: Descriptor =
    named("/noaclservice").withCalls(
      restCall(Method.GET, "/mocks/:id", getMock _)
    )
}

class NoAclServiceImpl extends NoAclService {
  def getMock(id: String) = ServiceCall { _ => Future.successful(NotUsed) }
}
