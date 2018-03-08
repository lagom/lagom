/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import java.util
import java.util.Optional

import com.lightbend.lagom.scaladsl.api.transport._
import org.scalatest.{ Matchers, WordSpec }

import scala.collection.immutable

/**
 *
 */
class ExceptionsSpec extends WordSpec with Matchers {

  val protocolTextPlain = MessageProtocol(Some("text/plain"), Some("utf-8"))
  val protocolJson = MessageProtocol(Some("application/json"), Some("utf-8"))
  val protocolHtml = MessageProtocol(Some("text/html"), Some("utf-8"))

  val supportedExceptions: immutable.Seq[TransportException] = List(
    DeserializationException("some msg - DeserializationException"),
    BadRequest("some msg - BadRequest"),
    Forbidden("some msg - Forbidden"),
    PolicyViolation("some msg - PolicyViolation"),
    NotFound("some msg - NotFound"),
    NotAcceptable(List(protocolJson, protocolTextPlain), protocolHtml),
    PayloadTooLarge("some msg - PayloadTooLarge"),
    UnsupportedMediaType(protocolTextPlain, protocolJson),
    SerializationException("some msg - SerializationException")
  )

  "TransportExceptions" should {

    supportedExceptions.foreach { ex =>
      s"be buildable from code and message (${ex.getClass.getName})" in {
        val reconstructed = TransportException.fromCodeAndMessage(ex.errorCode, ex.exceptionMessage)
        reconstructed.getClass.getName should ===(ex.getClass.getName)
        reconstructed.exceptionMessage should ===(ex.exceptionMessage)
      }
    }

  }
}
