/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api.deser

import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer._
import com.lightbend.lagom.scaladsl.api.transport.DeserializationException
import com.lightbend.lagom.scaladsl.api.transport.MessageProtocol
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.api.libs.json._
import scala.collection.immutable.Seq

class MessageSerializerSpec extends WordSpec with Matchers {
  case class Dummy(prop: Option[String])

  "ByteString-to-PlayJson (via JsValueMessageSerializer)" should {
    "deserialize empty ByteString as JSON null" in {
      val deserializer = JsValueMessageSerializer.deserializer(MessageProtocol.empty)
      deserializer.deserialize(ByteString.empty) shouldBe JsNull
    }
  }

  implicit def optionFormat[T: Format]: Format[Option[T]] = new Format[Option[T]] {
    override def reads(json: JsValue): JsResult[Option[T]] = json.validateOpt[T]
    override def writes(o: Option[T]): JsValue = o match {
      case Some(t) => implicitly[Writes[T]].writes(t)
      case None    => JsNull
    }
  }

  "PlayJson-to-RequestPayload formatters" should {
    implicit val format: Format[Dummy] = Json.format

    "fail when converting JSNull into T." in {
      intercept[JsResultException] {
        JsNull.as[Dummy]
      }
    }

    "convert JS null to None by default" in {
      val dummy = JsNull.as[Option[Dummy]]
      dummy shouldBe None
    }
  }

  "ByteString-to-RequestPayload (for JSON payloads, using jsValueFormatMessageSerializer)" should {
    "deserialize empty ByteString's to Option[T] as None" in {
      val serializer = jsValueFormatMessageSerializer(JsValueMessageSerializer, optionFormat[String])
      val out        = serializer.deserializer(MessageProtocol.empty).deserialize(ByteString.empty)
      out shouldBe None
    }

    "fail to deserialize empty ByteString to Dummy(prop: Option[T])" in {
      val format: Format[Dummy] = Json.format
      val serializer            = jsValueFormatMessageSerializer(JsValueMessageSerializer, format)

      intercept[DeserializationException] {
        serializer.deserializer(MessageProtocol.empty).deserialize(ByteString.empty)
      }
    }
  }

  "ByteString-to-ByteString" should {
    "serialize any request of type ByteString to the same ByteSting" in {
      val serializer = NoopMessageSerializer.serializerForRequest
      val out        = serializer.serialize(ByteString("sample string"))
      out shouldBe ByteString("sample string")
    }

    "serialize any response of type ByteString to the same ByteSting" in {
      val serializer = NoopMessageSerializer.serializerForResponse(Seq(MessageProtocol.empty))
      val out        = serializer.serialize(ByteString("sample string"))
      out shouldBe ByteString("sample string")
    }

    "deserialize any ByteString's to the same ByteSting" in {
      val deserializer = NoopMessageSerializer.deserializer(MessageProtocol.empty)
      val out          = deserializer.deserialize(ByteString("sample string"))
      out shouldBe ByteString("sample string")
    }
  }
}
