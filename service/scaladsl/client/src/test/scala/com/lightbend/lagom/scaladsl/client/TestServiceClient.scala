/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.broker.Topic

import scala.collection.immutable

object TestServiceClient extends ServiceClientConstructor {
  override def construct[S <: Service](constructor: (ServiceClientImplementationContext) => S): S = {
    constructor(new ServiceClientImplementationContext {
      override def resolve(descriptor: Descriptor): ServiceClientContext = {
        new ServiceClientContext {
          override def createServiceCall[Request, Response](methodName: String, params: immutable.Seq[Any]): ServiceCall[Request, Response] = {
            TestServiceCall(descriptor, methodName, params)
          }
          override def createTopic[Message](methodName: String): Topic[Message] = {
            TestTopic(descriptor, methodName)
          }
        }
      }
    })
  }
}
