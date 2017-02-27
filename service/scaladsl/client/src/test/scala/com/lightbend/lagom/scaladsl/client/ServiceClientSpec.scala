/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.{ Subscriber, Topic }
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import org.scalatest.{ Inside, Matchers, WordSpec }

import scala.collection.immutable
import scala.concurrent.Future

class ServiceClientSpec extends WordSpec with Matchers with Inside {

  "The service client macro" should {
    "allow implementing a service client" in {
      val mockServiceClient = TestServiceClient.implement[MockService]

      inside(mockServiceClient.noParamList) {
        case TestServiceCall(descriptor, methodName, params) =>
          descriptor.name should ===("mockservice")
          methodName should ===("noParamList")
          params should be(Nil)
      }
      inside(mockServiceClient.noParamListRef) {
        case TestServiceCall(descriptor, methodName, params) =>
          descriptor.name should ===("mockservice")
          methodName should ===("noParamListRef")
          params should be(Nil)
      }
      inside(mockServiceClient.noArguments()) {
        case TestServiceCall(descriptor, methodName, params) =>
          descriptor.name should ===("mockservice")
          methodName should ===("noArguments")
          params should be(Nil)
      }
      inside(mockServiceClient.noArgumentsRef()) {
        case TestServiceCall(descriptor, methodName, params) =>
          descriptor.name should ===("mockservice")
          methodName should ===("noArgumentsRef")
          params should be(Nil)
      }
      inside(mockServiceClient.oneArgument("foo")) {
        case TestServiceCall(descriptor, methodName, params) =>
          descriptor.name should ===("mockservice")
          methodName should ===("oneArgument")
          params should ===(Seq("foo"))
      }
      inside(mockServiceClient.twoArguments("foo", "bar")) {
        case TestServiceCall(descriptor, methodName, params) =>
          descriptor.name should ===("mockservice")
          methodName should ===("twoArguments")
          params should ===(Seq("foo", "bar"))
      }
      inside(mockServiceClient.twoPrimitiveArguments(1L, 2)) {
        case TestServiceCall(descriptor, methodName, params) =>
          descriptor.name should ===("mockservice")
          methodName should ===("twoPrimitiveArguments")
          params should ===(Seq(1, 2))
          params(0) shouldBe a[java.lang.Long]
          params(1) shouldBe an[java.lang.Integer]
      }
      inside(mockServiceClient.streamNoParamList) {
        case TestTopic(descriptor, methodName) =>
          descriptor.name should ===("mockservice")
          methodName should ===("streamNoParamList")
      }
      inside(mockServiceClient.streamNoParamListRef) {
        case TestTopic(descriptor, methodName) =>
          descriptor.name should ===("mockservice")
          methodName should ===("streamNoParamListRef")
      }
      inside(mockServiceClient.streamNoArguments()) {
        case TestTopic(descriptor, methodName) =>
          descriptor.name should ===("mockservice")
          methodName should ===("streamNoArguments")
      }
      inside(mockServiceClient.streamNoArgumentsRef()) {
        case TestTopic(descriptor, methodName) =>
          descriptor.name should ===("mockservice")
          methodName should ===("streamNoArgumentsRef")
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
            override def createTopic[Message](methodName: String): Topic[Message] = {
              TestTopic(descriptor, methodName)
            }
          }
        }
      })
    }
  }

}

trait MockService extends Service {
  def noParamList: ServiceCall[String, String]
  def noParamListRef: ServiceCall[String, String]
  def noArguments(): ServiceCall[String, String]
  def noArgumentsRef(): ServiceCall[String, String]
  def oneArgument(arg: String): ServiceCall[String, String]
  def twoArguments(arg1: String, arg2: String): ServiceCall[String, String]
  def twoPrimitiveArguments(arg1: Long, arg2: Int): ServiceCall[String, String]
  def streamNoParamList: Topic[String]
  def streamNoParamListRef: Topic[String]
  def streamNoArguments(): Topic[String]
  def streamNoArgumentsRef(): Topic[String]

  import Service._
  override def descriptor: Descriptor = named("mockservice").withCalls(
    call(noParamListRef _),
    call(noParamList),
    call(noArgumentsRef _),
    call(noArguments()),
    pathCall("/one/:arg", oneArgument _),
    pathCall("/two/:arg1/:arg2", twoArguments _)
  ).withTopics(
      topic("someid1", streamNoParamList),
      topic("someid2", streamNoParamListRef _),
      topic("someid3", streamNoArguments()),
      topic("someid4", streamNoArgumentsRef _)
    )
}

case class TestServiceCall[Request, Response](descriptor: Descriptor, methodName: String, params: Seq[Any]) extends ServiceCall[Request, Response] {
  override def invoke(request: Request): Future[Response] = null
}

case class TestTopic[Message](descriptor: Descriptor, methodName: String) extends Topic[Message] {
  override def topicId: TopicId = null
  override def subscribe: Subscriber[Message] = null
}
