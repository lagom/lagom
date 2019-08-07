/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api.deser

import java.util.UUID

import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.WordSpec

import scala.collection.immutable

class PathParamSerializerSpec extends WordSpec with Matchers with OptionValues {

  "PathParamSerializer" when {

    def spec[T](obj: T, serializedValue: String)(implicit serializer: PathParamSerializer[T]) {
      val serialized = immutable.Seq(serializedValue)

      s"serialize value" in {
        serializer.serialize(obj) should be(serialized)
      }
      s"deserialize value" in {
        serializer.deserialize(serialized) should be(obj)
      }
    }

    "using String path param" should {
      spec("hello", "hello")
    }

    "using Double path param" should {
      spec(30d, "30.0")
    }

    "using UUID path param" should {
      spec(UUID.fromString("00000000-0000-0000-0000-000000000000"), "00000000-0000-0000-0000-000000000000")
    }

    "using String value class path param" should {
      spec(StringAnyVal("hello"), "hello")
    }

    "using UUID value class path param" should {
      spec(UuidAnyVal(UUID.fromString("00000000-0000-0000-0000-000000000000")), "00000000-0000-0000-0000-000000000000")
    }

  }
}

case class StringAnyVal(value: String) extends AnyVal
case class UuidAnyVal(uuid: UUID)      extends AnyVal
