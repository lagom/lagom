/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.tools.tests.scaladsl

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, _ }

import scala.concurrent.Future

trait AclService extends Service {

  def getMock(id: String): ServiceCall[NotUsed, NotUsed]

  def addMock: ServiceCall[NotUsed, NotUsed]

  def descriptor: Descriptor =
    named("/aclservice").withCalls(
      restCall(Method.GET, "/scala-mocks/:id", getMock _),
      restCall(Method.POST, "/scala-mocks", addMock)
    ).withAutoAcl(true)

}

class AclServiceImpl extends AclService {
  def getMock(id: String) = ServiceCall { _ => Future.successful(NotUsed) }

  def addMock: ServiceCall[NotUsed, NotUsed] = ServiceCall { _ => Future.successful(NotUsed) }
}
