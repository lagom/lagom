/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.services.serializers

package explicitserializers {

  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall

  //#explicit-serializers
  import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer

  trait HelloService extends Service {
    def sayHello: ServiceCall[String, String]

    override def descriptor = {
      import Service._

      named("hello").withCalls(
        call(sayHello)(MessageSerializer.StringMessageSerializer, MessageSerializer.StringMessageSerializer)
      )
    }
  }
  //#explicit-serializers
}

package differentserializers {

  import akka.NotUsed
  import akka.util.ByteString
  import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer
  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall

  //#case-class-two-formats
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  case class MyMessage(id: String)

  object MyMessage {

    implicit val format: Format[MyMessage] = Json.format
    val alternateFormat: Format[MyMessage] = {
      (__ \ "identifier")
        .format[String]
        .inmap(MyMessage.apply, _.id)
    }
  }
  //#case-class-two-formats

  //#descriptor-two-formats
  trait MyService extends Service {
    def getMessage: ServiceCall[NotUsed, MyMessage]
    def getMessageAlternate: ServiceCall[NotUsed, MyMessage]

    override def descriptor = {
      import Service._

      named("my-service").withCalls(
        call(getMessage),
        call(getMessageAlternate)(
          implicitly[MessageSerializer[NotUsed, ByteString]],
          MessageSerializer.jsValueFormatMessageSerializer(
            implicitly[MessageSerializer[JsValue, ByteString]],
            MyMessage.alternateFormat
          )
        )
      )
    }
  }
  //#descriptor-two-formats
}

package customserializers {

  //#plain-text-serializer
  import akka.util.ByteString
  import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.NegotiatedSerializer
  import com.lightbend.lagom.scaladsl.api.transport.DeserializationException
  import com.lightbend.lagom.scaladsl.api.transport.MessageProtocol
  import com.lightbend.lagom.scaladsl.api.transport.NotAcceptable
  import com.lightbend.lagom.scaladsl.api.transport.UnsupportedMediaType

  class PlainTextSerializer(val charset: String) extends NegotiatedSerializer[String, ByteString] {
    override val protocol = MessageProtocol(Some("text/plain"), Some(charset))

    def serialize(s: String) = ByteString.fromString(s, charset)
  }
  //#plain-text-serializer

  //#json-text-serializer
  import play.api.libs.json.Json
  import play.api.libs.json.JsString

  class JsonTextSerializer extends NegotiatedSerializer[String, ByteString] {

    override val protocol = MessageProtocol(Some("application/json"))

    def serialize(s: String) =
      ByteString.fromString(Json.stringify(JsString(s)))
  }
  //#json-text-serializer

  //#plain-text-deserializer
  import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.NegotiatedDeserializer

  class PlainTextDeserializer(val charset: String) extends NegotiatedDeserializer[String, ByteString] {

    def deserialize(bytes: ByteString) =
      bytes.decodeString(charset)
  }
  //#plain-text-deserializer

  //#json-text-deserializer
  import scala.util.control.NonFatal

  class JsonTextDeserializer extends NegotiatedDeserializer[String, ByteString] {

    def deserialize(bytes: ByteString) = {
      try {
        Json.parse(bytes.iterator.asInputStream).as[String]
      } catch {
        case NonFatal(e) => throw DeserializationException(e)
      }
    }
  }
  //#json-text-deserializer

  //#text-serializer
  import com.lightbend.lagom.scaladsl.api.deser.StrictMessageSerializer

  class TextMessageSerializer extends StrictMessageSerializer[String] {
    //#text-serializer

    //#text-serializer-protocols
    override def acceptResponseProtocols = List(
      MessageProtocol(Some("text/plain")),
      MessageProtocol(Some("application/json"))
    )
    //#text-serializer-protocols

    //#text-serializer-request
    def serializerForRequest = new PlainTextSerializer("utf-8")
    //#text-serializer-request

    //#text-deserializer
    def deserializer(protocol: MessageProtocol) = {
      protocol.contentType match {
        case Some("text/plain") | None =>
          new PlainTextDeserializer(protocol.charset.getOrElse("utf-8"))
        case Some("application/json") =>
          new JsonTextDeserializer
        case _ =>
          throw UnsupportedMediaType(protocol, MessageProtocol(Some("text/plain")))
      }
    }
    //#text-deserializer

    //#text-serializer-response
    import scala.collection.immutable

    def serializerForResponse(accepted: immutable.Seq[MessageProtocol]) = {
      accepted match {
        case Nil => new PlainTextSerializer("utf-8")
        case protocols =>
          protocols
            .collectFirst {
              case MessageProtocol(Some("text/plain" | "text/*" | "*/*" | "*"), charset, _) =>
                new PlainTextSerializer(charset.getOrElse("utf-8"))
              case MessageProtocol(Some("application/json"), _, _) =>
                new JsonTextSerializer
            }
            .getOrElse {
              throw NotAcceptable(accepted, MessageProtocol(Some("text/plain")))
            }
      }
    }
    //#text-serializer-response
  }

}

package protobuf {

  import java.io.InputStream
  import java.io.OutputStream

  // Not real protobuf generated class...
  object Order {
    def parseFrom(is: InputStream): Order = null
  }

  class Order {
    def writeTo(os: OutputStream) {}
  }

  //#protobuf
  import akka.util.ByteString
  import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.NegotiatedDeserializer
  import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.NegotiatedSerializer
  import com.lightbend.lagom.scaladsl.api.deser.StrictMessageSerializer
  import com.lightbend.lagom.scaladsl.api.transport.MessageProtocol

  import scala.collection.immutable

  class ProtobufSerializer extends StrictMessageSerializer[Order] {

    private final val serializer = {
      new NegotiatedSerializer[Order, ByteString]() {
        override def protocol: MessageProtocol =
          MessageProtocol(Some("application/octet-stream"))

        def serialize(order: Order) = {
          val builder = ByteString.createBuilder
          order.writeTo(builder.asOutputStream)
          builder.result
        }
      }
    }

    private final val deserializer = {
      new NegotiatedDeserializer[Order, ByteString] {
        override def deserialize(bytes: ByteString) =
          Order.parseFrom(bytes.iterator.asInputStream)
      }
    }

    override def serializerForRequest =
      serializer
    override def deserializer(protocol: MessageProtocol) =
      deserializer
    override def serializerForResponse(
        acceptedMessageProtocols: immutable.Seq[MessageProtocol]
    ) = serializer
  }
  //#protobuf
}
