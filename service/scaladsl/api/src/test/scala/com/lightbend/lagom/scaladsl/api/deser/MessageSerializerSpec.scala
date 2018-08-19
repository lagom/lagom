package com.lightbend.lagom.scaladsl.api.deser

import MessageSerializer._
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.transport.{DeserializationException, MessageProtocol}
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json._

class MessageSerializerSpec extends WordSpec with Matchers {
  case class Dummy(prop: Option[String])

  "JsValueMessageSerializer" should {
    "deserialize successfully empty ByteString as JSON null" in {
      val deserializer = JsValueMessageSerializer.deserializer(MessageProtocol.empty)
      deserializer.deserialize(ByteString.empty) shouldBe JsNull
    }
  }

  "jsValueFormatMessageSerializer" should {
    implicit def optionFormat[T: Format]: Format[Option[T]] = new Format[Option[T]] {
      override def reads(json: JsValue): JsResult[Option[T]] = json.validateOpt[T]

      override def writes(o: Option[T]): JsValue = o match {
        case Some(t) ⇒ implicitly[Writes[T]].writes(t)
        case None ⇒ JsNull
      }
    }

    "deserialize successfully empty ByteString to Option[T]" in {
      val serializer = jsValueFormatMessageSerializer(JsValueMessageSerializer, optionFormat[String])
      val out = serializer.deserializer(MessageProtocol.empty).deserialize(ByteString.empty)
      out shouldBe None
    }

    "fail to deserialize empty ByteString to Dummy(prop: Option[T])" in {
      val format: Format[Dummy] = Json.format
      val serializer = jsValueFormatMessageSerializer(JsValueMessageSerializer, format)

      intercept[DeserializationException] {
        serializer.deserializer(MessageProtocol.empty).deserialize(ByteString.empty)
      }
    }
  }
}
