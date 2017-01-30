/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.deser

import java.io.{ CharArrayWriter, PrintWriter }
import java.util.Base64

import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.transport.{ ExceptionMessage, MessageProtocol, TransportErrorCode, TransportException }
import play.api.libs.json.{ JsError, JsSuccess, Json }
import play.api.{ Environment, Mode }

import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.util.control.NonFatal

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

/**
 * The default exception serializer.
 *
 * Serializes exception messages to JSON.
 *
 * This serializer is capable of converting Lagom built-in exceptions to and from JSON. Custom sub classes of
 * TransportException can also be deserialized by extending this class and overriding [[fromCodeAndMessage()]].
 */
class DefaultExceptionSerializer(environment: Environment) extends ExceptionSerializer {

  override def serialize(exception: Throwable, accept: Seq[MessageProtocol]): RawExceptionMessage = {
    val (errorCode, message) = exception match {
      case te: TransportException =>
        (te.errorCode, te.exceptionMessage)
      case e if environment.mode == Mode.Prod =>
        // By default, don't give out information about generic exceptions.
        (TransportErrorCode.InternalServerError, new ExceptionMessage("Exception", ""))
      case e =>
        // Ok to give out exception information in dev and test
        val writer = new CharArrayWriter
        e.printStackTrace(new PrintWriter(writer))
        val detail = writer.toString
        (TransportErrorCode.InternalServerError, new ExceptionMessage(s"${exception.getClass.getName}: ${exception.getMessage}", detail))
    }

    val messageBytes = ByteString.fromString(Json.stringify(Json.obj(
      "name" -> message.name,
      "detail" -> message.detail
    )))

    RawExceptionMessage(errorCode, MessageProtocol(Some("application/json"), None, None), messageBytes)
  }

  override def deserialize(message: RawExceptionMessage): Throwable = {
    val messageJson = try {
      Json.parse(message.message.iterator.asInputStream)
    } catch {
      case NonFatal(e) =>
        Json.obj()
    }

    val jsonParseResult = for {
      name <- (messageJson \ "name").validate[String]
      detail <- (messageJson \ "detail").validate[String]
    } yield new ExceptionMessage(name, detail)

    val exceptionMessage = jsonParseResult match {
      case JsSuccess(m, _) => m
      case JsError(_)      => new ExceptionMessage("UndeserializableException", message.message.utf8String)
    }

    fromCodeAndMessage(message.errorCode, exceptionMessage)
  }

  /**
   * Override this if you wish to deserialize your own custom Exceptions.
   *
   * The default implementation delegates to [[TransportException.fromCodeAndMessage()]], which will return a best match
   * Lagom built-in exception.
   *
   * @param transportErrorCode The transport error code.
   * @param exceptionMessage The exception message.
   * @return The exception.
   */
  protected def fromCodeAndMessage(transportErrorCode: TransportErrorCode, exceptionMessage: ExceptionMessage): Throwable = {
    TransportException.fromCodeAndMessage(transportErrorCode, exceptionMessage)
  }
}

object DefaultExceptionSerializer {

  /**
   * Unresolved exception serializer, allows it to be injected later.
   */
  object Unresolved extends ExceptionSerializer {
    override def serialize(exception: Throwable, accept: Seq[MessageProtocol]): RawExceptionMessage =
      throw new NotImplementedError("Cannot use unresolved exception serializer")

    override def deserialize(message: RawExceptionMessage): Throwable =
      throw new NotImplementedError("Cannot use unresolved exception serializer")
  }
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
    protocol.charset match {
      case Some(charset) => message.decodeString(charset)
      case None if protocol.contentType.contains("application/json") => message.decodeString("utf-8")
      case None => Base64.getEncoder.encodeToString(message.toArray)
    }
  }
}

object RawExceptionMessage {

  def apply(errorCode: TransportErrorCode, protocol: MessageProtocol, message: ByteString): RawExceptionMessage =
    RawExceptionMessageImpl(errorCode, protocol, message)

  private case class RawExceptionMessageImpl(errorCode: TransportErrorCode, protocol: MessageProtocol, message: ByteString) extends RawExceptionMessage
}
