/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api

import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.deser.ExceptionSerializer
import com.lightbend.lagom.scaladsl.api.deser.RawExceptionMessage
import com.lightbend.lagom.scaladsl.api.transport._
import play.api.Environment
import play.api.Mode

import scala.collection.immutable.Seq
import scala.util.control.NoStackTrace
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 *
 */
class ExceptionsSpec extends AnyWordSpec with Matchers {
  val protocolTextPlain = MessageProtocol(Some("text/plain"), Some("utf-8"))
  val protocolJson      = MessageProtocol(Some("application/json"), Some("utf-8"))
  val protocolHtml      = MessageProtocol(Some("text/html"), Some("utf-8"))

  val supportedTransportExceptions: Seq[TransportException] = Seq(
    DeserializationException("some msg - DeserializationException"),
    BadRequest("some msg - BadRequest"),
    Unauthorized("some msg - Unauthorized"),
    Forbidden("some msg - Forbidden"),
    PolicyViolation("some msg - PolicyViolation"),
    NotFound("some msg - NotFound"),
    NotAcceptable(List(protocolJson, protocolTextPlain), protocolHtml),
    PayloadTooLarge("some msg - PayloadTooLarge"),
    UnsupportedMediaType(protocolTextPlain, protocolJson),
    TooManyRequests("some msg - TooManyRequests"),
    SerializationException("some msg - SerializationException")
  )

  val envModes = Seq(Mode.Dev, Mode.Test, Mode.Prod)

  "Lagom-provided TransportExceptions" should {
    supportedTransportExceptions.foreach { transportException =>
      s"be buildable from code and message (${transportException.getClass.getName})" in {
        val reconstructed =
          TransportException.fromCodeAndMessage(transportException.errorCode, transportException.exceptionMessage)
        reconstructed.getClass.getName should ===(transportException.getClass.getName)
        reconstructed.exceptionMessage should ===(transportException.exceptionMessage)
      }

      // TODO: move this to DefaultExceptionSerializerSpec (?)
      envModes.foreach { mode =>
        s"be rebuilt after a full de/ser roundtrip using the DefaultExceptionSerializer ($mode - ${transportException.getClass.getName})" in {
          val serializer: ExceptionSerializer = new DefaultExceptionSerializer(Environment.simple(mode = mode))
          val reconstructed = serializer
            .deserialize(serializer.serialize(transportException, Seq(protocolJson)))
            .asInstanceOf[TransportException]
          reconstructed.getClass.getName should ===(transportException.getClass.getName)
          reconstructed.exceptionMessage should ===(transportException.exceptionMessage)
        }
      }
    }

    // TODO: move this to an integration-test or DefaultExceptionSerializerSpec (?)
    val customException = new CustomException("Some message")

    val transportExceptionsWithCause = Seq(
      new TransportException(TransportErrorCode.BadRequest, customException),
      DeserializationException(customException),
      BadRequest(customException),
      Unauthorized(customException),
      Forbidden(customException),
      PolicyViolation(customException),
      NotFound(customException),
      PayloadTooLarge(customException),
      TooManyRequests(customException),
      SerializationException(customException)
    )

    transportExceptionsWithCause.foreach { transportException =>
      envModes.foreach { mode =>
        s"be rebuilt into a user-provided cause after a full de/ser roundtrip using a custom ExceptionSerializer ($mode - ${transportException.getClass.getName})" in {
          val serializer: ExceptionSerializer = new CustomExceptionSerializer(Environment.simple(mode = mode))
          val reconstructed                   = serializer.deserialize(serializer.serialize(transportException, Seq(protocolJson)))
          reconstructed.getClass.getName should ===(customException.getClass.getName)
          reconstructed.getMessage should ===(customException.customMessage)
        }
      }
    }
  }
}

class CustomException(val customMessage: String) extends Exception(customMessage) with NoStackTrace

class CustomExceptionSerializer(environment: Environment) extends ExceptionSerializer {
  private val delegate = new DefaultExceptionSerializer(environment)

  private val MARK          = ByteString('#')
  private val METADATA_MARK = ByteString('@')

  override def serialize(exception: Throwable, accept: Seq[MessageProtocol]): RawExceptionMessage = {
    val rawMessage = delegate.serialize(exception, accept)

    // MARK should be escaped, this is not a production ready Serializer)
    if (exception.isInstanceOf[TransportException] && exception.getCause != null) {
      val causeName           = ByteString(s"${exception.getCause.getClass.getName}")
      val causeMessage        = ByteString(s"${exception.getCause.getMessage}")
      val metadata            = causeName ++ MARK ++ causeMessage
      val messageWithMetadata = metadata ++ METADATA_MARK ++ rawMessage.message
      // CustomException#the-message@delegateRawMessage
      RawExceptionMessage(rawMessage.errorCode, rawMessage.protocol, messageWithMetadata)
    } else {
      rawMessage
    }
  }

  override def deserialize(rawMessage: RawExceptionMessage): Throwable = {
    val throwable = delegate.deserialize(rawMessage)
    val (b1, b2)  = rawMessage.message.span(_ != METADATA_MARK.head)
    (b1, b2) match {
      case (_, ByteString.empty) => throwable
      case (head, _) =>
        head.span(_ != MARK.head) match {
          case (name, msg) if name == ByteString(classOf[CustomException].getName) =>
            new CustomException(msg.tail.utf8String)
          case _ => throwable
        }
    }
  }
}
