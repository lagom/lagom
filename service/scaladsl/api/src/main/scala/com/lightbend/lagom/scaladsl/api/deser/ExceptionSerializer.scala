/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.deser

import java.util.Base64

import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.transport.{ MessageProtocol, TransportErrorCode }

import scala.collection.immutable
import scala.collection.immutable.Seq

/**
 * Handles the serialization and deserialization of exceptions.
 */
trait ExceptionSerializer {
  /**
   * Serialize the given exception to an exception message.
   *
   * The raw exception message consists of an error code, a message protocol, and a message entity to send across the
   * wire.
   *
   * The exception serializer may attempt to match one of the protocols passed into the accept parameter.
   *
   * @param exception The exception to serialize.
   * @param accept    The accepted protocols.
   * @return The raw exception message.
   */
  def serialize(exception: Throwable, accept: immutable.Seq[MessageProtocol]): RawExceptionMessage

  /**
   * Deserialize an exception message into an exception.
   *
   * The exception serializer should make a best effort attempt at deserializing the message, but should not expect
   * the message to be in any particular format.  If it cannot deserialize the message, it should return a generic
   * exception, it should not itself throw an exception.
   *
   * @param message The message to deserialize.
   * @return The deserialized exception.
   */
  def deserialize(message: RawExceptionMessage): Throwable
}

object DefaultExceptionSerializer extends ExceptionSerializer {
  /**
   * Serialize the given exception to an exception message.
   *
   * The raw exception message consists of an error code, a message protocol, and a message entity to send across the
   * wire.
   *
   * The exception serializer may attempt to match one of the protocols passed into the accept parameter.
   *
   * @param exception The exception to serialize.
   * @param accept    The accepted protocols.
   * @return The raw exception message.
   */
  override def serialize(exception: Throwable, accept: Seq[MessageProtocol]): RawExceptionMessage = ???

  /**
   * Deserialize an exception message into an exception.
   *
   * The exception serializer should make a best effort attempt at deserializing the message, but should not expect
   * the message to be in any particular format.  If it cannot deserialize the message, it should return a generic
   * exception, it should not itself throw an exception.
   *
   * @param message The message to deserialize.
   * @return The deserialized exception.
   */
  override def deserialize(message: RawExceptionMessage): Throwable = ???
}

/**
 * A serialized exception message.
 *
 * A serialized exception message consists of a transport error code, a protocol, and a message body. All, some or none
 * of these details may be sent over the wire when the error is sent, depending on what the underlying protocol
 * supports.
 *
 * Some protocols have a maximum limit on the amount of data that can be sent with an error message, eg for WebSockets,
 * the WebSocket close frame can have a maximum payload of 125 bytes.  While it's up to the transport implementation
 * itself to enforce this limit and gracefully handle when the message exceeds this, exception serializers should be
 * aware of this when producing exception messages.
 */
sealed trait RawExceptionMessage {

  /**
   * The error code.
   *
   * This will be sent as an HTTP status code, or WebSocket close code.
   */
  val errorCode: TransportErrorCode

  /**
   * The protocol.
   */
  val protocol: MessageProtocol

  /**
   * The message.
   */
  val message: ByteString

  /**
   * Get the message as text.
   *
   * If this is a binary message (that is, the message protocol does not define a charset), encodes it using Base64.
   */
  def messageAsText: String = {
    protocol.charset.fold(Base64.getEncoder.encodeToString(message.toArray)) { charset =>
      message.decodeString(charset)
    }
  }
}

object RawExceptionMessage {

  def apply(errorCode: TransportErrorCode, protocol: MessageProtocol, message: ByteString): RawExceptionMessage =
    RawExceptionMessageImpl(errorCode, protocol, message)

  private case class RawExceptionMessageImpl(errorCode: TransportErrorCode, protocol: MessageProtocol, message: ByteString) extends RawExceptionMessage
}
