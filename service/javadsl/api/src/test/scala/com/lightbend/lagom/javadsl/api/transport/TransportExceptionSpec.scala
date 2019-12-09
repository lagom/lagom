/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.transport

import java.util
import java.util.Optional

import com.lightbend.lagom.javadsl.api.deser.DeserializationException
import com.lightbend.lagom.javadsl.api.deser.SerializationException

import scala.collection.immutable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 *
 */
class TransportExceptionSpec extends AnyWordSpec with Matchers {
  val protocolTextPlain = new MessageProtocol(Optional.of("text/plain"), Optional.of("utf-8"), Optional.empty[String])
  val protocolJson      = new MessageProtocol(Optional.of("application/json"), Optional.of("utf-8"), Optional.empty[String])
  val protocolHtml      = new MessageProtocol(Optional.of("text/html"), Optional.of("utf-8"), Optional.empty[String])

  val supportedExceptions: immutable.Seq[TransportException] = List(
    new DeserializationException("some msg - DeserializationException"),
    new BadRequest("some msg - BadRequest"),
    new Forbidden("some msg - Forbidden"),
    new PolicyViolation("some msg - PolicyViolation"),
    new NotFound("some msg - NotFound"),
    new NotAcceptable(util.Arrays.asList(protocolJson, protocolTextPlain), protocolHtml),
    new PayloadTooLarge("some msg - PayloadTooLarge"),
    new UnsupportedMediaType(protocolTextPlain, protocolJson),
    new SerializationException("some msg - SerializationException")
  )

  "Lagom-provided TransportExceptions" should {
    supportedExceptions.foreach { ex =>
      s"be buildable from code and message (${ex.getClass.getName})" in {
        val reconstructed = TransportException.fromCodeAndMessage(ex.errorCode(), ex.exceptionMessage())
        reconstructed.getClass.getName should ===(ex.getClass.getName)
        reconstructed.exceptionMessage() should ===(ex.exceptionMessage())
      }
    }

    // TODO: implement roundtrip de/ser tests like in com.lightbend.lagom.scaladsl.api.ExceptionsSpec
  }
}
