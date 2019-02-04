/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api

import java.util.UUID

import Service._
import com.lightbend.lagom.scaladsl.api.Descriptor.CallImpl
import com.lightbend.lagom.scaladsl.api.Descriptor.PathCallIdImpl
import com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.WordSpec

class ServiceSupportSpec extends WordSpec with Matchers with OptionValues {

  "ServiceSupport macro" when {

    "using String path params support service" should {
      val holder = new StringMockService {
        override def foo(bar: String): ServiceCall[String, String] = null
      }.descriptor.calls.collectFirst {
        case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) =>
          holder
      }

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
      }.descriptor.calls.collectFirst {
        case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) =>
          holder
      }

      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should ===(PathParamSerializer.DoublePathParamSerializer)
      }
    }

    "using UUID path params support service" should {
      val holder = new UuidMockService {
        override def foo(bar: UUID): ServiceCall[String, String] = null
      }.descriptor.calls.collectFirst {
        case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) =>
          holder
      }

      "resolve the method name" in {
        holder.value.method.getDeclaringClass should ===(classOf[UuidMockService])
        holder.value.method.getName should ===("foo")
      }
      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should ===(PathParamSerializer.UuidPathParamSerializer)
      }
    }

    "using String value class path params support service" should {
      val holder = new StringAnyValMockService {
        override def foo(bar: StringAnyVal): ServiceCall[String, String] = null
      }.descriptor.calls.collectFirst {
        case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) =>
          holder
      }

      "resolve the method name" in {
        holder.value.method.getDeclaringClass should ===(classOf[StringAnyValMockService])
        holder.value.method.getName should ===("foo")
      }
      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should be(a[PathParamSerializer[_]])
      }
    }

    "using UUID value class path params support service" should {
      val holder = new UuidAnyValMockService {
        override def foo(bar: UuidAnyVal): ServiceCall[String, String] = null
      }.descriptor.calls.collectFirst {
        case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) =>
          holder
      }

      "resolve the method name" in {
        holder.value.method.getDeclaringClass should ===(classOf[UuidAnyValMockService])
        holder.value.method.getName should ===("foo")
      }
      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should be(a[PathParamSerializer[_]])
      }
    }

    "using Vector[String] query params support service" should {
      val holder = new VectorStringMockService {
        override def foo(bar: Vector[String]): ServiceCall[String, String] = null
      }.descriptor.calls.collectFirst {
        case CallImpl(PathCallIdImpl("/foo?bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) =>
          holder
      }

      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should be(a[PathParamSerializer[_]])
      }
    }

    "using List[Double] query params support service" should {
      val holder = new ListDoubleMockService {
        override def foo(bar: List[Double]): ServiceCall[String, String] = null
      }.descriptor.calls.collectFirst {
        case CallImpl(PathCallIdImpl("/foo?bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) =>
          holder
      }

      "pass the path param serializers" in {
        holder.value.pathParamSerializers should have size 1
        holder.value.pathParamSerializers.head should be(a[PathParamSerializer[_]])
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

trait UuidMockService extends Service {

  def foo(bar: UUID): ServiceCall[String, String]

  override def descriptor = named("mock").withCalls(
    pathCall("/foo/:bar", foo _)
  )
}

case class StringAnyVal(value: String) extends AnyVal
trait StringAnyValMockService extends Service {

  def foo(bar: StringAnyVal): ServiceCall[String, String]

  override def descriptor = named("mock").withCalls(
    pathCall("/foo/:bar", foo _)
  )
}

case class UuidAnyVal(value: UUID) extends AnyVal
trait UuidAnyValMockService extends Service {

  def foo(bar: UuidAnyVal): ServiceCall[String, String]

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
