/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
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

  // 1

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

  // 2

  /**
   * A particular operation was forbidden.
   */
  val Forbidden: TransportErrorCode = TransportErrorCode(403, 4403, "Forbidden")

  // 3

  /**
   * A generic error to used to indicate that the end receiving the error message violated the remote ends policy.
   */
  val PolicyViolation: TransportErrorCode = TransportErrorCode(404, 1008, "Policy Violation")
  /**
   * A resource was not found, equivalent to policy violation.
   */
  val NotFound: TransportErrorCode = PolicyViolation

  // 4

  /**
   * The method being used is not allowed.
   */
  val MethodNotAllowed: TransportErrorCode = TransportErrorCode(405, 4405, "Method Not Allowed")

  // 5

  /**
   * The server can't generate a response that meets the clients accepted response types.
   */
  val NotAcceptable: TransportErrorCode = TransportErrorCode(406, 4406, "Not Acceptable")

  // 6

  /**
   * The payload of a message is too large.
   */
  val PayloadTooLarge: TransportErrorCode = TransportErrorCode(413, 1009, "Payload Too Large")

  // 7

  /**
   * The client or server doesn't know how to deserialize the request or response.
   */
  val UnsupportedMediaType: TransportErrorCode = TransportErrorCode(415, 4415, "Unsupported Media Type")

  // 8

  /**
   * A generic error used to indicate that the end sending the error message because it encountered an unexpected
   * condition.
   */
  val UnexpectedCondition: TransportErrorCode = TransportErrorCode(500, 1011, "Unexpected Condition")
  /**
   * An internal server error, equivalent to Unexpected Condition.
   */
  val InternalServerError: TransportErrorCode = UnexpectedCondition

  // 9

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
    this(errorCode, new ExceptionMessage(classOf[TransportException].getSimpleName, message), null)

  def this(errorCode: TransportErrorCode, cause: Throwable) =
    this(errorCode, new ExceptionMessage(classOf[TransportException].getSimpleName, cause.getMessage), cause)

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
  def fromCodeAndMessage(errorCode: TransportErrorCode, exceptionMessage: ExceptionMessage): TransportException =
    ByNameTransportExceptions.get(exceptionMessage.name)
      .orElse(ByCodeTransportExceptions.get(errorCode))
      .fold(new TransportException(errorCode, exceptionMessage))(_.apply(exceptionMessage))

  private final val ByNameTransportExceptions: Map[String, (ExceptionMessage) => TransportException] = Map(
    classOf[DeserializationException].getSimpleName -> (new DeserializationException(_)),
    classOf[BadRequest].getSimpleName -> (new BadRequest(_)),
    classOf[Forbidden].getSimpleName -> (new Forbidden(_)),
    classOf[PolicyViolation].getSimpleName -> (new PolicyViolation(_)),
    classOf[NotFound].getSimpleName -> (new NotFound(_)),
    classOf[NotAcceptable].getSimpleName -> (new NotAcceptable(_)),
    classOf[PayloadTooLarge].getSimpleName -> (new PayloadTooLarge(_)),
    classOf[SerializationException].getSimpleName -> (new SerializationException(_)),
    classOf[UnsupportedMediaType].getSimpleName -> (new UnsupportedMediaType(_))
  )

  private final val ByCodeTransportExceptions: Map[TransportErrorCode, (ExceptionMessage) => TransportException] = Map(
    DeserializationException.errorCode -> (new DeserializationException(_)),
    Forbidden.errorCode -> (new Forbidden(_)),
    PolicyViolation.errorCode -> (new PolicyViolation(_)),
    NotAcceptable.errorCode -> (new NotAcceptable(_)),
    PayloadTooLarge.errorCode -> (new PayloadTooLarge(_)),
    UnsupportedMediaType.errorCode -> (new UnsupportedMediaType(_))
  )

}

final class UnsupportedMediaType(exceptionMessage: ExceptionMessage) extends TransportException(UnsupportedMediaType.errorCode, exceptionMessage)

object UnsupportedMediaType {
  final val errorCode = TransportErrorCode.UnsupportedMediaType

  def apply(received: MessageProtocol, supported: MessageProtocol): UnsupportedMediaType =
    new UnsupportedMediaType(
      new ExceptionMessage(
        classOf[UnsupportedMediaType].getSimpleName,
        s"Could not negotiate a deserializer for type $received, the default media type supported is $supported"
      )
    )
}

final class NotAcceptable(exceptionMessage: ExceptionMessage) extends TransportException(NotAcceptable.errorCode, exceptionMessage)

object NotAcceptable {
  final val errorCode = TransportErrorCode.NotAcceptable

  def apply(requested: immutable.Seq[MessageProtocol], supported: MessageProtocol) =
    new NotAcceptable(new ExceptionMessage(
      classOf[NotAcceptable].getSimpleName,
      s"The requested protocol type/versions: (${requested.mkString(", ")}) could not be satisfied by the server, the default that the server uses is: $supported"
    ))
}

final class SerializationException(exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(SerializationException.errorCode, exceptionMessage, cause) {
  def this(exceptionMessage: ExceptionMessage) = this(exceptionMessage, null)
}

object SerializationException {
  final val errorCode = TransportErrorCode.InternalServerError

  def apply(message: String) = new SerializationException(
    new ExceptionMessage(classOf[SerializationException].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new SerializationException(
    new ExceptionMessage(classOf[SerializationException].getSimpleName, errorCode.description), cause
  )
}

final class DeserializationException(exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(DeserializationException.errorCode, exceptionMessage, cause) {
  def this(exceptionMessage: ExceptionMessage) = this(exceptionMessage, null)
}

object DeserializationException {
  final val errorCode = TransportErrorCode.UnsupportedData

  def apply(message: String) = new DeserializationException(
    new ExceptionMessage(classOf[DeserializationException].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new DeserializationException(
    new ExceptionMessage(classOf[DeserializationException].getSimpleName, errorCode.description), cause
  )
}

final class PolicyViolation(exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(PolicyViolation.errorCode, exceptionMessage, cause) {
  def this(exceptionMessage: ExceptionMessage) = this(exceptionMessage, null)
}

object PolicyViolation {
  final val errorCode = TransportErrorCode.PolicyViolation

  def apply(message: String) = new PolicyViolation(
    new ExceptionMessage(classOf[PolicyViolation].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new PolicyViolation(
    new ExceptionMessage(classOf[PolicyViolation].getSimpleName, errorCode.description), cause
  )
}

final class NotFound(exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(NotFound.errorCode, exceptionMessage, cause) {
  def this(exceptionMessage: ExceptionMessage) = this(exceptionMessage, null)
}

object NotFound {
  final val errorCode = TransportErrorCode.NotFound

  def apply(message: String) = new NotFound(
    new ExceptionMessage(classOf[NotFound].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new NotFound(
    new ExceptionMessage(classOf[NotFound].getSimpleName, errorCode.description), cause
  )
}

final class Forbidden(exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(Forbidden.errorCode, exceptionMessage, cause) {
  def this(exceptionMessage: ExceptionMessage) = this(exceptionMessage, null)
}

object Forbidden {
  final val errorCode = TransportErrorCode.Forbidden

  def apply(message: String) = new Forbidden(
    new ExceptionMessage(classOf[Forbidden].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new Forbidden(
    new ExceptionMessage(classOf[Forbidden].getSimpleName, errorCode.description), cause
  )
}

final class PayloadTooLarge(exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(PayloadTooLarge.errorCode, exceptionMessage, cause) {
  def this(exceptionMessage: ExceptionMessage) = this(exceptionMessage, null)
}

object PayloadTooLarge {
  final val errorCode = TransportErrorCode.PayloadTooLarge

  def apply(message: String) = new PayloadTooLarge(
    new ExceptionMessage(classOf[PayloadTooLarge].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new PayloadTooLarge(
    new ExceptionMessage(classOf[PayloadTooLarge].getSimpleName, errorCode.description), cause
  )
}

final class BadRequest(exceptionMessage: ExceptionMessage, cause: Throwable) extends TransportException(BadRequest.errorCode, exceptionMessage, cause) {
  def this(exceptionMessage: ExceptionMessage) = this(exceptionMessage, null)
}

object BadRequest {
  final val errorCode = TransportErrorCode.BadRequest

  def apply(message: String) = new BadRequest(
    new ExceptionMessage(classOf[BadRequest].getSimpleName, message), null
  )

  def apply(cause: Throwable) = new BadRequest(
    new ExceptionMessage(classOf[BadRequest].getSimpleName, errorCode.description), cause
  )
}
