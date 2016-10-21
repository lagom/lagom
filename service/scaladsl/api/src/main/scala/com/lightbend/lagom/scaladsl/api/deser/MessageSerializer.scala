/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.deser

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.transport.{ MessageProtocol, NotAcceptable, SerializationException, UnsupportedMediaType }
import com.sun.xml.internal.ws.encoding.soap.DeserializationException

import scala.collection.immutable

trait MessageSerializer[Message, WireFormat] {
  /**
   * The message headers that will be accepted for response serialization.
   */
  def acceptResponseProtocols: immutable.Seq[MessageProtocol] = Nil

  /**
   * Whether this serializer serializes values that are used or not.
   *
   * If false, it means this serializer is for an empty request/response, eg, they use the
   * [[akka.NotUsed]] type.
   *
   * @return Whether the values this serializer serializes are used.
   */
  def isUsed: Boolean = true

  /**
   * Whether this serializer is a streamed serializer or not.
   *
   * @return Whether this is a streamed serializer.
   */
  def isStreamed: Boolean = false

  /**
   * Get a serializer for a client request.
   *
   * Since a client is the initiator of the request, it simply returns the default serializer for the entity.
   *
   * @return A serializer for request messages.
   */
  def serializerForRequest: MessageSerializer.NegotiatedSerializer[Message, WireFormat]

  /**
   * Get a deserializer for an entity described by the given request or response protocol.
   *
   * @param protocol The protocol of the message request or response associated with teh entity.
   * @return A deserializer for request/response messages.
   * @throws UnsupportedMediaType If the deserializer can't deserialize that protocol.
   */
  @throws[UnsupportedMediaType]
  def deserializer(protocol: MessageProtocol): MessageSerializer.NegotiatedDeserializer[Message, WireFormat]

  /**
   * Negotiate a serializer for the response, given the accepted message headers.
   *
   * @param acceptedMessageProtocols The accepted message headers is a list of message headers that will be accepted by
   *                                 the client. Any empty values in a message protocol, including the list itself,
   *                                 indicate that any format is acceptable.
   * @throws NotAcceptable If the serializer can't meet the requirements of any of the accept headers.
   */
  @throws[NotAcceptable]
  def serializerForResponse(acceptedMessageProtocols: immutable.Seq[MessageProtocol]): MessageSerializer.NegotiatedSerializer[Message, WireFormat]
}

/**
 * A strict message serializer, for messages that fit and are worked with strictly in memory.
 *
 * Strict message serializers differ from streamed serializers, in that they work directly with `ByteString`, rather
 * than an Akka streams `Source`.
 */
trait StrictMessageSerializer[Message] extends MessageSerializer[Message, ByteString]

/**
 * A streamed message serializer, for streams of messages.
 */
trait StreamedMessageSerializer[Message] extends MessageSerializer[Source[Message, _], Source[ByteString, _]] {
  override def isStreamed: Boolean = true
}

object MessageSerializer {

  /**
   * A negotiated serializer.
   *
   * @tparam Message The type of message that this serializer serializes.
   * @tparam WireFormat The wire format that this serializer serializes to.
   */
  trait NegotiatedSerializer[Message, WireFormat] {

    /**
     * Get the protocol associated with this message.
     */
    def protocol: MessageProtocol = MessageProtocol(None, None, None)

    /**
     * Serialize the given message.
     *
     * @param message The message to serialize.
     * @return The serialized message.
     */
    @throws[SerializationException]
    def serialize(message: Message): WireFormat
  }

  /**
   * A negotiated deserializer.
   *
   * @tparam Message The type of message that this serializer serializes.
   * @tparam WireFormat The wire format that this serializer serializes to.
   */
  trait NegotiatedDeserializer[Message, WireFormat] {

    /**
     * Deserialize the given wire format.
     *
     * @param wire The raw wire data.
     * @return The deserialized message.
     */
    @throws[DeserializationException]
    def deserialize(wire: WireFormat): Message
  }

  implicit val StringMessageSerializer: StrictMessageSerializer[String] = new StrictMessageSerializer[String] {

    val defaultProtocol = MessageProtocol(Some("text/plain"), Some("utf-8"), None)

    class StringSerializer(override val protocol: MessageProtocol) extends NegotiatedSerializer[String, ByteString] {
      override def serialize(s: String) = ByteString.fromString(s, protocol.charset.getOrElse("utf-8"))
    }

    class StringDeserializer(charset: String) extends NegotiatedDeserializer[String, ByteString] {
      override def deserialize(wire: ByteString) = wire.decodeString(charset)
    }

    override val serializerForRequest = new StringSerializer(defaultProtocol)

    override def deserializer(protocol: MessageProtocol) = {
      if (protocol.contentType.forall(_ == "text/plain")) {
        new StringDeserializer(protocol.charset.getOrElse("utf-8"))
      } else {
        throw UnsupportedMediaType(protocol, defaultProtocol)
      }
    }

    override def serializerForResponse(acceptedMessageProtocols: immutable.Seq[MessageProtocol]) = {
      if (acceptedMessageProtocols.isEmpty) {
        serializerForRequest
      } else {
        acceptedMessageProtocols.collectFirst {
          case wildcardOrNone if wildcardOrNone.contentType.forall(ct => ct == "*" || ct == "*/*") =>
            new StringSerializer(wildcardOrNone.withContentType("text/plain"))
          case textPlain if textPlain.contentType.contains("text/plain") =>
            new StringSerializer(textPlain)
        } match {
          case Some(serializer) => serializer
          case None             => throw NotAcceptable(acceptedMessageProtocols, defaultProtocol)
        }
      }
    }

  }

  implicit val NotUsedMessageSerializer: StrictMessageSerializer[NotUsed] = new StrictMessageSerializer[NotUsed] {
    override def serializerForRequest = new NegotiatedSerializer[NotUsed, ByteString] {
      override def serialize(message: NotUsed): ByteString = ByteString.empty
    }

    override def deserializer(messageProtocol: MessageProtocol) = new NegotiatedDeserializer[NotUsed, ByteString] {
      override def deserialize(wire: ByteString) = NotUsed
    }

    override def serializerForResponse(acceptedMessageProtocols: immutable.Seq[MessageProtocol]) = new NegotiatedSerializer[NotUsed, ByteString] {
      override def serialize(message: NotUsed): ByteString = ByteString.empty
    }

    override def isUsed: Boolean = false
  }
}
