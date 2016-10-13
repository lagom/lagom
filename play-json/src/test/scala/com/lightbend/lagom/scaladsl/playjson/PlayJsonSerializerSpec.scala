/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import akka.actor.ActorSystem
import akka.serialization.{ SerializationExtension, SerializerWithStringManifest }
import akka.testkit.TestKit
import com.lightbend.lagom.scaladsl.playjson.PlayJsonSerializationRegistry.Entry
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }
import play.api.libs.json.Json

import scala.collection.immutable.Seq

case class Event1(name: String, increment: Int) extends Jsonable

class TestRegistry1 extends PlayJsonSerializationRegistry {
  override def serializers: Seq[Entry[_]] =
    Seq(Entry(classOf[Event1], Json.reads[Event1], Json.writes[Event1]))
}

class PlayJsonSerializerSpec extends WordSpec with Matchers {

  "The PlayJsonSerializer" should {

    "pick up serializers from configured registry" in {
      val config = ConfigFactory.parseString(
        """
          lagom.serialization.play-json.serialization-registry=com.lightbend.lagom.scaladsl.playjson.TestRegistry1
        """
      ).withFallback(ConfigFactory.load())

      var system: ActorSystem = null
      try {
        system = ActorSystem("PlayJsonSerializerSpec-1", config)

        val event = Event1("test", 1)
        val serializeExt = SerializationExtension(system)
        val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]

        val bytes = serializer.toBinary(event)
        val manifest = serializer.manifest(event)

        bytes.isEmpty should be(false)

        val deserialized = serializer.fromBinary(bytes, manifest)
        deserialized should be(event)

      } finally {
        if (system ne null) TestKit.shutdownActorSystem(system)
      }

    }

  }

}
