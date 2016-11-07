/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import org.scalatest.{ Inside, Matchers, WordSpec }

import scala.collection.immutable
import scala.concurrent.Future

class ServiceClientSpec extends WordSpec with Matchers with Inside {

  "The service client macro" should {
    "allow implementing a service client" in {
      val mockServiceClient = TestServiceClient.implement[MockService]

      inside(mockServiceClient.noParamList) {
        case TestServiceCall(descriptor, methodName, params) => {
          descriptor.name should ===("mockservice")
          methodName should ===("noParamList")
          params should be(Nil)
        }
      }
      inside(mockServiceClient.noArguments()) {
        case TestServiceCall(descriptor, methodName, params) => {
          descriptor.name should ===("mockservice")
          methodName should ===("noArguments")
          params should be(Nil)
        }
      }
      inside(mockServiceClient.oneArgument("foo")) {
        case TestServiceCall(descriptor, methodName, params) => {
          descriptor.name should ===("mockservice")
          methodName should ===("oneArgument")
          params should ===(Seq("foo"))
        }
      }
      inside(mockServiceClient.twoArguments("foo", "bar")) {
        case TestServiceCall(descriptor, methodName, params) => {
          descriptor.name should ===("mockservice")
          methodName should ===("twoArguments")
          params should ===(Seq("foo", "bar"))
        }
      }
    }
  }

  object TestServiceClient extends ServiceClientConstructor {
    override def construct[S <: Service](constructor: (ServiceClientImplementationContext) => S): S = {
      constructor(new ServiceClientImplementationContext {
        override def resolve(descriptor: Descriptor): ServiceClientContext = {
          new ServiceClientContext {
            override def createServiceCall[Request, Response](methodName: String, params: immutable.Seq[Any]): ServiceCall[Request, Response] = {
              TestServiceCall(descriptor, methodName, params)
            }
          }
        }
      })
    }
  }

}

trait MockService extends Service {
  def noParamList: ServiceCall[String, String]
  def noArguments(): ServiceCall[String, String]
  def oneArgument(arg: String): ServiceCall[String, String]
  def twoArguments(arg1: String, arg2: String): ServiceCall[String, String]

  import Service._
  override def descriptor: Descriptor = named("mockservice").withCalls(
    call(noParamList _),
    call(noArguments _),
    pathCall("/one/:arg", oneArgument _),
    pathCall("/two/:arg1/:arg2", twoArguments _)
  )
}

case class TestServiceCall[Request, Response](descriptor: Descriptor, methodName: String, params: Seq[Any]) extends ServiceCall[Request, Response] {
  override def invoke(request: Request): Future[Response] = null
}
