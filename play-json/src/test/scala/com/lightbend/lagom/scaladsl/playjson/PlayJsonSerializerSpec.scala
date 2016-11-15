/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.serialization.{ SerializationExtension, SerializerWithStringManifest }
import akka.testkit.TestKit
import akka.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ Matchers, WordSpec }
import play.api.libs.json._

import scala.collection.immutable.{ Seq, SortedMap }

case class Event1(name: String, increment: Int) extends Jsonable
case class Event2(name: String, inner: Inner) extends Jsonable
case class Inner(on: Boolean)
case class MigratedEvent(addedField: Int, newName: String) extends Jsonable

class TestRegistry1 extends SerializerRegistry {

  implicit val innerFormat = Json.format[Inner]

  override def serializers: Seq[Serializers[_]] =
    Seq(
      Serializers(Json.reads[Event1], Json.writes[Event1]),
      Serializers(Json.format[Event2])
    )

}

class TestRegistry2 extends SerializerRegistry {
  override def serializers =
    Seq(
      Serializers(Json.format[MigratedEvent]),
      Serializers(Json.format[Event1])
    )

  import play.api.libs.json._
  override def migrations = Map(
    Migrations.transform[MigratedEvent](
      // something like a history of changes, if version is oldest each one needs to be applied
      SortedMap(
        // remove field (not really needed, here for completeness)
        1 -> (__ \ "removedField").json.prune,
        // add a field with some default value
        2 -> __.json.update((__ \ "addedField").json.put(JsString("2"))),
        // a field was renamed, this one is tricky -
        // copy value first, using "update", then remove the old key using "prune"
        3 -> __.json.update((__ \ "newName").json.copyFrom((__ \ "oldName").json.pick))
          .andThen((__ \ "oldName").json.prune),
        4 ->
          // a field changed type
          (__ \ "addedField").json.update(Format.of[JsString].map { case JsString(value) => JsNumber(value.toInt) })
      )
    ),
    Migrations.renamed("event1.old.ClassName", inVersion = 2, toClass = classOf[Event1])
  )
}

class TestRegistry3 extends SerializerRegistry {
  override def serializers = Seq(
    Serializers(Json.format[MigratedEvent])
  )

  // manual way to do the same transformations (compared to json transformations above)
  override def migrations: Map[String, Migration] = Map(
    classOf[MigratedEvent].getName -> new Migration(5) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = {
        var toUpdate = json
        if (fromVersion < 2) {
          toUpdate = toUpdate - "removedField"
        }
        if (fromVersion < 3) {
          toUpdate = toUpdate + ("addedField" -> JsString("2"))
        }
        if (fromVersion < 4) {
          val fieldValue = (toUpdate \ "oldName").get
          toUpdate = toUpdate - "oldName"
          toUpdate = toUpdate + ("newName" -> fieldValue)
        }
        if (fromVersion < 5) {
          val oldValAsInt = (toUpdate \ "addedField").get match {
            case JsString(str) => JsNumber(str.toInt)
            case _             => throw new RuntimeException("Unexpected value type")
          }
          toUpdate = toUpdate + ("addedField" -> oldValAsInt)
        }
        toUpdate
      }
    }
  )
}

case class Box(surprise: Option[String])

class PlayJsonSerializerSpec extends WordSpec with Matchers {

  "The PlayJsonSerializer" should {

    "pick up serializers from configured registry" in withActorSystem(ConfigFactory.parseString(
      """
          lagom.serialization.play-json.serialization-registry=com.lightbend.lagom.scaladsl.playjson.TestRegistry1
      """
    ).withFallback(ConfigFactory.load())) { system =>

      val serializeExt = SerializationExtension(system)
      List(Event1("test", 1), Event2("test2", Inner(on = true))).foreach { event =>
        val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]

        val bytes = serializer.toBinary(event)
        val manifest = serializer.manifest(event)

        bytes.isEmpty should be(false)

        val deserialized = serializer.fromBinary(bytes, manifest)
        deserialized should be(event)
      }
    }

    "apply sequential migrations using json-transformations" in withActorSystem(ConfigFactory.parseString(
      """
        lagom.serialization.play-json.serialization-registry=com.lightbend.lagom.scaladsl.playjson.TestRegistry2
      """
    ).withFallback(ConfigFactory.load())) { system =>

      val expectedEvent = MigratedEvent(addedField = 2, newName = "some value")
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "removedField" -> JsString("doesn't matter"),
        "oldName" -> JsString("some value")
      ))).getBytes(StandardCharsets.UTF_8)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]

      val deserialized = serializer.fromBinary(oldJsonBytes, classOf[MigratedEvent].getName + "#1")
      deserialized should be(expectedEvent)

    }

    "apply migrations written imperatively" in withActorSystem(ConfigFactory.parseString(
      """
        lagom.serialization.play-json.serialization-registry=com.lightbend.lagom.scaladsl.playjson.TestRegistry3
      """
    ).withFallback(ConfigFactory.load())) { system =>

      val expectedEvent = MigratedEvent(addedField = 2, newName = "some value")
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "removedField" -> JsString("doesn't matter"),
        "oldName" -> JsString("some value")
      ))).getBytes(StandardCharsets.UTF_8)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]

      val deserialized = serializer.fromBinary(oldJsonBytes, classOf[MigratedEvent].getName + "#1")
      deserialized should be(expectedEvent)

    }

    "apply rename migration" in withActorSystem(ConfigFactory.parseString(
      """
        lagom.serialization.play-json.serialization-registry=com.lightbend.lagom.scaladsl.playjson.TestRegistry2
      """
    ).withFallback(ConfigFactory.load())) { system =>

      val event = Event1("something", 25)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]

      val bytes = serializer.toBinary(event)
      val deserialized = serializer.fromBinary(bytes, "event1.old.ClassName")

      deserialized should be(event)

    }

  }

  "The provided serializers" should {

    object Singleton

    "serialize and deserialize singletons" in {
      val serializer = Serializers.emptySingletonFormat(Singleton)

      val result = serializer.reads(serializer.writes(Singleton))
      result.isSuccess shouldBe true
      result.get shouldBe Singleton
    }

    "serialize and deserialize options" in {
      val serializer = Serializers.optionFormat[String]

      val someResult = serializer.reads(serializer.writes(Some("string")))
      someResult.isSuccess shouldBe true
      someResult.get shouldBe Some("string")

      val noneResult = serializer.reads(serializer.writes(None))
      noneResult.isSuccess shouldBe true
      noneResult.get shouldBe None
    }

    "serialize and deserialize options inside macro serializers" in {

      // box should pick up the implicit serializer?
      import Serializers.Implicits._

      val serializer = Json.format[Box]

      val noneResult = serializer.reads(serializer.writes(Box(None)))
      noneResult.isSuccess shouldBe true
      noneResult.get shouldBe Box(None)

      val someResult = serializer.reads(serializer.writes(Box(Some("surprise"))))
      someResult.isSuccess shouldBe true
      someResult.get shouldBe Box(Some("surprise"))
    }

  }

  private var counter = 0
  def withActorSystem(config: Config)(test: ActorSystem => Unit): Unit = {
    var system: ActorSystem = null
    try {
      counter += 1
      system = ActorSystem(s"PlayJsonSerializerSpec-$counter", config)
      test(system)
    } finally {
      if (system ne null) TestKit.shutdownActorSystem(system)
    }
  }

}
