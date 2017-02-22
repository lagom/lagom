/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.transport

import java.io.Serializable

import scala.collection.immutable

/**
 * An error code that gets translated into an appropriate underlying error code.
 *
 * This attempts to match up corresponding HTTP error codes with WebSocket close codes, so that user code can
 * generically select a code without worrying about the underlying transport.
 *
 * While most WebSocket close codes that we typically use do have a corresponding HTTP error code, there are many
 * HTTP error codes that don't have a corresponding WebSocket close code.  In these cases, we use the private WebSocket
 * close code range (4xxx), with the HTTP error code as the last three digits.  Such WebSocket close codes will be in
 * the range 4400 to 4599.
 *
 * This class should only be used to represent error codes, status codes like HTTP 200 should not be represented using
 * this class.  This is enforced for HTTP codes, since they have a well defined categorisation, codes between 400 and
 * 599 are considered errors.  It is however not enforced for WebSockets, since the WebSocket protocol defines no such
 * categorisation of codes, it specifies a number of well known codes from 1000 to 1015, with no particular pattern to
 * their meaning, and the remaining codes are only categorised by whether they are private, reserved for the WebSocket
 * spec, or reserved for applications to specify.
 *
 * For WebSocket close codes that are not known, or are not in the private range of 4400 to 4599 defined by us, this use
 * class uses the generic HTTP 404 error code.
 */
sealed trait TransportErrorCode extends Serializable {
  /**
   * The HTTP status code for this error.
   */
  val http: Int

  /**
   * The WebSocket close code for this error.
   */
  val webSocket: Int

  /**
   * A description of this close code.
   *
   * This description will be meaningful for known built in close codes, but for other codes, it will be
   * `Unknown error code`
   *
   * @return A description of this closed code.
   */
  val description: String
}

object TransportErrorCode {

  def apply(http: Int, webSocket: Int, description: String): TransportErrorCode =
    TransportErrorCodeImpl(http, webSocket, description)

  /**
   * A protocol error, or bad request.
   */
  val ProtocolError: TransportErrorCode = TransportErrorCode(400, 1002, "Protocol Error/Bad Request")
  /**
   * An application level protocol error, such as when a client or server sent data that can't be deserialized.
   */
  val UnsupportedData: TransportErrorCode = TransportErrorCode(400, 1003, "Unsupported Data/Bad Request")
  /**
   * A bad request, most often this will be equivalent to unsupported data.
   */
  val BadRequest: TransportErrorCode = UnsupportedData
  /**
   * A particular operation was forbidden.
   */
  val Forbidden: TransportErrorCode = TransportErrorCode(403, 4403, "Forbidden")
  /**
   * A generic error to used to indicate that the end receiving the error message violated the remote ends policy.
   */
  val PolicyViolation: TransportErrorCode = TransportErrorCode(404, 1008, "Policy Violation")
  /**
   * A resource was not found, equivalent to policy violation.
   */
  val NotFound: TransportErrorCode = PolicyViolation
  /**
   * The method being used is not allowed.
   */
  val MethodNotAllowed: TransportErrorCode = TransportErrorCode(405, 4405, "Method Not Allowed")
  /**
   * The server can't generate a response that meets the clients accepted response types.
   */
  val NotAcceptable: TransportErrorCode = TransportErrorCode(406, 4406, "Not Acceptable")
  /**
   * The payload of a message is too large.
   */
  val PayloadTooLarge: TransportErrorCode = TransportErrorCode(413, 1009, "Payload Too Large")
  /**
   * The client or server doesn't know how to deserialize the request or response.
   */
  val UnsupportedMediaType: TransportErrorCode = TransportErrorCode(415, 4415, "Unsupported Media Type")
  /**
   * A generic error used to indicate that the end sending the error message because it encountered an unexpected
   * condition.
   */
  val UnexpectedCondition: TransportErrorCode = TransportErrorCode(500, 1011, "Unexpected Condition")
  /**
   * An internal server error, equivalent to Unexpected Condition.
   */
  val InternalServerError: TransportErrorCode = UnexpectedCondition
  /**
   * Service unavailable, thrown when the service is unavailable or going away.
   */
  val ServiceUnavailable: TransportErrorCode = TransportErrorCode(503, 1001, "Going Away/Service Unavailable")
  /**
   * Going away, thrown when the service is unavailable or going away.
   */
  val GoingAway: TransportErrorCode = ServiceUnavailable

  private val allErrorCodes = Seq(ProtocolError, UnsupportedData, Forbidden, PolicyViolation, MethodNotAllowed, NotAcceptable,
    PayloadTooLarge, UnsupportedMediaType, UnexpectedCondition, ServiceUnavailable)
  private val HttpErrorCodeMap = allErrorCodes.map(code => code.http -> code).toMap
  private val WebSocketErrorCodeMap = allErrorCodes.map(code => code.webSocket -> code).toMap

  /**
   * Get a transport error code from the given HTTP error code.
   *
   * @param code The HTTP error code, must be between 400 and 599 inclusive.
   * @return The transport error code.
   * @throws IllegalArgumentException if the HTTP code was not between 400 and 599.
   */
  def fromHttp(code: Int): TransportErrorCode = {
    HttpErrorCodeMap.get(code) match {
      case Some(errorCode) => errorCode
      case None if code > 599 || code < 100 =>
        throw new IllegalArgumentException("Invalid http status code: " + code)
      case None if code < 400 =>
        throw new IllegalArgumentException("Invalid http error code: " + code)
      case None => TransportErrorCode(code, 4000 + code, "Unknown error code")
    }
  }

  /**
   * Get a transport error code from the given WebSocket close code.
   *
   * @param code The WebSocket close code, must be between 0 and 65535 inclusive.
   * @return The transport error code.
   * @throws IllegalArgumentException if the code is not an unsigned 2 byte integer.
   */
  def fromWebSocket(code: Int): TransportErrorCode = {
    WebSocketErrorCodeMap.get(code) match {
      case Some(errorCode) => errorCode
      case None if code < 0 || code > 65535 =>
        throw new IllegalArgumentException("Invalid WebSocket status code: " + code)
      case None if code >= 4400 && code <= 4599 => TransportErrorCode(code - 4000, code, "Unknown error code")
      case None                                 => TransportErrorCode(404, code, "Unknown error code")
    }
  }

  private case class TransportErrorCodeImpl(http: Int, webSocket: Int, description: String) extends TransportErrorCode {
    override def toString = s"$http/$webSocket $description"
  }
}

/**
 * A high level exception message.
 *
 * This is used by the default exception serializer to serialize exceptions into messages.
 *
 * @param name The name of the exception. This is usually the simple name of the class of the exception.
 * @param detail The detailed description of the exception.
 */
final class ExceptionMessage(val name: String, val detail: String) extends Serializable {
  override def equals(other: Any): Boolean = other match {
    case that: ExceptionMessage =>
      name == that.name &&
        detail == that.detail
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(name, detail)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString = s"ExceptionMessage($name, $detail)"
}

/**
 * An exception that can be translated down to a specific error in the transport.
 *
 * Transport exceptions describe the HTTP/WebSocket error code associated with them, allowing Lagom to send an error
 * code specific to the type of error. They also have an exception message, which is used both to capture exception
 * details, as well as to identify the exception, so that the right type of exception can be deserialized at the other
 * end.
 */
class TransportException(val errorCode: TransportErrorCode, val exceptionMessage: ExceptionMessage, cause: Throwable) extends RuntimeException(exceptionMessage.detail, cause) {

  def this(errorCode: TransportErrorCode, message: String) =
    this(errorCode, new ExceptionMessage(getClass.getSimpleName, message), null)

  def this(errorCode: TransportErrorCode, cause: Throwable) =
    this(errorCode, new ExceptionMessage(getClass.getSimpleName, cause.getMessage), cause)

  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) =
    this(errorCode, exceptionMessage, null)

  override def toString: String = s"${super.toString} ($errorCode)"
}

object TransportException {

  /**
   * Convert an error code and exception message to an exception.
   *
   * This will only return Lagom built in exceptions. A custom exception that extends [[TransportException]] that has
   * been serialized by the [[com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer]] will not be returned
   * by this method, instead, a best match based on the HTTP/WebSocket error code will be selected.
   *
   * @param errorCode The error code.
   * @param exceptionMessage The exception message.
   * @return A built in TransportException that best matches the given error code and exception message.
   */
  def fromCodeAndMessage(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage): TransportException = {
    ByNameTransportExceptions.get(exceptionMessage.name).orElse {
      ByCodeTransportExceptions.get(errorCode)
    }.fold(new TransportException(errorCode, exceptionMessage))(_.apply(errorCode, exceptionMessage))
  }

  private val ByNameTransportExceptions: Map[String, (TransportErrorCode, ExceptionMessage) => TransportException] = Map(
    classOf[DeserializationException].getSimpleName -> ((tec, em) => new DeserializationException(tec, em)),
    classOf[SerializationException].getSimpleName -> ((tec, em) => new SerializationException(tec, em)),
    classOf[UnsupportedMediaType].getSimpleName -> ((tec, em) => new UnsupportedMediaType(tec, em)),
    classOf[NotAcceptable].getSimpleName -> ((tec, em) => new NotAcceptable(tec, em)),
    classOf[PolicyViolation].getSimpleName -> ((tec, em) => new PolicyViolation(tec, em)),
    classOf[NotFound].getSimpleName -> ((tec, em) => new NotFound(tec, em)),
    classOf[PayloadTooLarge].getSimpleName -> ((tec, em) => new PayloadTooLarge(tec, em)),
    classOf[Forbidden].getSimpleName -> ((tec, em) => new Forbidden(tec, em))
  )

  private val ByCodeTransportExceptions: Map[TransportErrorCode, (TransportErrorCode, ExceptionMessage) => TransportException] = Map(
    DeserializationException.ErrorCode -> ((tec, em) => new DeserializationException(tec, em)),
    UnsupportedMediaType.ErrorCode -> ((tec, em) => new UnsupportedMediaType(tec, em)),
    NotAcceptable.ErrorCode -> ((tec, em) => new NotAcceptable(tec, em)),
    PolicyViolation.ErrorCode -> ((tec, em) => new PolicyViolation(tec, em)),
    PayloadTooLarge.ErrorCode -> ((tec, em) => new PayloadTooLarge(tec, em)),
    BadRequest.ErrorCode -> ((tec, em) => new BadRequest(tec, em)),
    Forbidden.ErrorCode -> ((tec, em) => new Forbidden(tec, em))
  )

}

final class UnsupportedMediaType(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) extends TransportException(errorCode, exceptionMessage)

object UnsupportedMediaType {
  val ErrorCode = TransportErrorCode.UnsupportedMediaType

  def apply(received: MessageProtocol, supported: MessageProtocol): UnsupportedMediaType =
    new UnsupportedMediaType(
      ErrorCode,
      new ExceptionMessage(
        classOf[UnsupportedMediaType].getSimpleName,
        s"Could not negotiate a deserializer for type $received, the default media type supported is $supported"
      )
    )
}

final class NotAcceptable(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) extends TransportException(errorCode, exceptionMessage)

object NotAcceptable {
  val ErrorCode = TransportErrorCode.NotAcceptable

  def apply(requested: immutable.Seq[MessageProtocol], supported: MessageProtocol) =
    new NotAcceptable(ErrorCode, new ExceptionMessage(
      classOf[NotAcceptable].getSimpleName,
      s"The requested protocol type/versions: (${requested.mkString(", ")}) could not be satisfied by the server, the default that the server uses is: $supported"
    ))
}

final class SerializationException(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(errorCode, exceptionMessage, cause) {
  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) = this(errorCode, exceptionMessage, null)
}

object SerializationException {
  val ErrorCode = TransportErrorCode.InternalServerError

  def apply(message: String) = new SerializationException(
    ErrorCode,
    new ExceptionMessage(classOf[SerializationException].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new SerializationException(
    ErrorCode,
    new ExceptionMessage(classOf[SerializationException].getSimpleName, cause.getMessage), cause
  )
}

final class DeserializationException(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(errorCode, exceptionMessage, cause) {
  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) = this(errorCode, exceptionMessage, null)
}

object DeserializationException {
  val ErrorCode = TransportErrorCode.UnsupportedData

  def apply(message: String) = new DeserializationException(
    ErrorCode,
    new ExceptionMessage(classOf[DeserializationException].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new DeserializationException(
    ErrorCode,
    new ExceptionMessage(classOf[DeserializationException].getSimpleName, cause.getMessage), cause
  )
}

final class PolicyViolation(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(errorCode, exceptionMessage, cause) {
  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) = this(errorCode, exceptionMessage, null)
}

object PolicyViolation {
  val ErrorCode = TransportErrorCode.PolicyViolation

  def apply(message: String) = new PolicyViolation(
    ErrorCode,
    new ExceptionMessage(classOf[PolicyViolation].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new PolicyViolation(
    ErrorCode,
    new ExceptionMessage(classOf[PolicyViolation].getSimpleName, cause.getMessage), cause
  )
}

final class NotFound(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(errorCode, exceptionMessage, cause) {
  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) = this(errorCode, exceptionMessage, null)
}

object NotFound {
  val ErrorCode = TransportErrorCode.NotFound

  def apply(message: String) = new NotFound(
    ErrorCode,
    new ExceptionMessage(classOf[NotFound].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new NotFound(
    ErrorCode,
    new ExceptionMessage(classOf[NotFound].getSimpleName, cause.getMessage), cause
  )
}

final class Forbidden(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(errorCode, exceptionMessage, cause) {
  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) = this(errorCode, exceptionMessage, null)
}

object Forbidden {
  val ErrorCode = TransportErrorCode.Forbidden

  def apply(message: String) = new Forbidden(
    ErrorCode,
    new ExceptionMessage(classOf[Forbidden].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new Forbidden(
    ErrorCode,
    new ExceptionMessage(classOf[Forbidden].getSimpleName, cause.getMessage), cause
  )
}

final class PayloadTooLarge(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(errorCode, exceptionMessage, cause) {
  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) = this(errorCode, exceptionMessage, null)
}

object PayloadTooLarge {
  val ErrorCode = TransportErrorCode.PayloadTooLarge

  def apply(message: String) = new PayloadTooLarge(
    ErrorCode,
    new ExceptionMessage(classOf[PayloadTooLarge].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new PayloadTooLarge(
    ErrorCode,
    new ExceptionMessage(classOf[PayloadTooLarge].getSimpleName, cause.getMessage), cause
  )
}

final class BadRequest(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(errorCode, exceptionMessage, cause) {
  def this(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage) = this(errorCode, exceptionMessage, null)
}

object BadRequest {
  val ErrorCode = TransportErrorCode.BadRequest

  def apply(message: String) = new BadRequest(
    ErrorCode,
    new ExceptionMessage(classOf[BadRequest].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new BadRequest(
    ErrorCode,
    new ExceptionMessage(classOf[BadRequest].getSimpleName, cause.getMessage), cause
  )
}
