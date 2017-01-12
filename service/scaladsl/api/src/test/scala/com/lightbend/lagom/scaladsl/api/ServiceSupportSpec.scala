/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import Service._
import com.lightbend.lagom.scaladsl.api.Descriptor.{ CallImpl, PathCallIdImpl }
import com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer
import org.scalatest.{ Matchers, OptionValues, WordSpec }

class ServiceSupportSpec extends WordSpec with Matchers with OptionValues {

  "ServiceSupport macro" should {

    val holder = new MockService {
      override def foo(bar: String): ServiceCall[String, String] = null
    }.descriptor.calls.collect {
      case CallImpl(PathCallIdImpl("/foo/:bar"), holder: ServiceSupport.ScalaMethodServiceCall[_, _], _, _, _, _) => holder
    }.headOption

    "resolve the method name" in {
      holder.value.method.getDeclaringClass should ===(classOf[MockService])
      holder.value.method.getName should ===("foo")
    }
    "pass the path param serializers " in {
      holder.value.pathParamSerializers should have size 1
      holder.value.pathParamSerializers.head should ===(PathParamSerializer.StringPathParamSerializer)
    }
  }
}

trait MockService extends Service {

  def foo(bar: String): ServiceCall[String, String]

  override def descriptor = named("mock").withCalls(
    pathCall("/foo/:bar", foo _)
  )

}
