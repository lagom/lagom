/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import Service._
import com.lightbend.lagom.scaladsl.api.Descriptor.{ CallImpl, PathCallIdImpl }
import com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer
import org.scalatest.{ Matchers, OptionValues, WordSpec }

class ServiceSupportSpec extends WordSpec with Matchers with OptionValues {

  "ServiceSupport macro" when {

    "using String path params support service" should {
      val holder = new StringMockService {
        override def foo(bar: String): ServiceCall[String, String] = null
      }.descriptor.calls.collect {
        case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) => holder
      }.headOption

      "resolve the method name" in {
        holder.value.method.getDeclaringClass should ===(classOf[StringMockService])
        holder.value.method.getName should ===("foo")
      }
      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should ===(PathParamSerializer.StringPathParamSerializer)
      }
    }

    "using Double path params support service" should {
      val holder = new DoubleMockService {
        override def foo(bar: Double): ServiceCall[String, String] = null
      }.descriptor.calls.collect {
        case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) => holder
      }.headOption

      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should ===(PathParamSerializer.DoublePathParamSerializer)
      }
    }

    "using Vector[String] path params support service" should {
      val holder = new VectorStringMockService {
        override def foo(bar: Vector[String]): ServiceCall[String, String] = null
      }.descriptor.calls.collect {
        case CallImpl(PathCallIdImpl("/foo?bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) => holder
      }.headOption

      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should be(a[PathParamSerializer[Vector[String]]])
      }
    }

    "using List[Double] path params support service" should {
      val holder = new ListDoubleMockService {
        override def foo(bar: List[Double]): ServiceCall[String, String] = null
      }.descriptor.calls.collect {
        case CallImpl(PathCallIdImpl("/foo?bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) => holder
      }.headOption

      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should be(a[PathParamSerializer[List[Double]]])
      }
    }
  }
}

trait StringMockService extends Service {

  def foo(bar: String): ServiceCall[String, String]

  override def descriptor = named("mock").withCalls(
    pathCall("/foo/:bar", foo _)
  )
}

trait DoubleMockService extends Service {

  def foo(bar: Double): ServiceCall[String, String]

  override def descriptor = named("mock").withCalls(
    pathCall("/foo/:bar", foo _)
  )
}

trait VectorStringMockService extends Service {

  def foo(bar: Vector[String]): ServiceCall[String, String]

  override def descriptor = named("mock").withCalls(
    pathCall("/foo?bar", foo _)
  )
}

trait ListDoubleMockService extends Service {

  def foo(bar: List[Double]): ServiceCall[String, String]

  override def descriptor = named("mock").withCalls(
    pathCall("/foo?bar", foo _)
  )
}
