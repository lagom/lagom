/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.serialization.{ SerializationExtension, SerializerWithStringManifest }
import akka.testkit.TestKit
import org.scalatest.{ Matchers, WordSpec }
import play.api.libs.json._

import scala.collection.immutable.{ Seq, SortedMap }

case class Event1(name: String, increment: Int)
object Event1 {
  implicit val format: Format[Event1] = Json.format[Event1]
}
case class Event2(name: String, inner: Inner)
case class Inner(on: Boolean)
case class MigratedEvent(addedField: Int, newName: String)

case class Name(name: String)

object Name {
  implicit val format: Format[Name] = new Format[Name] {
    override def writes(o: Name): JsValue = JsString(o.toString)

    override def reads(js: JsValue): JsResult[Name] = js.validate[String].map(Name.apply)
  }
}

object TestRegistry1 extends JsonSerializerRegistry {

  implicit val innerFormat = Json.format[Inner]

  override def serializers: Seq[JsonSerializer[_]] =
    Seq(
      JsonSerializer[Event1],
      JsonSerializer(Json.format[Event2])
    )

}

object TestRegistry2 extends JsonSerializerRegistry {
  override def serializers =
    Seq(
      JsonSerializer(Json.format[MigratedEvent]),
      JsonSerializer(Json.format[Event1])
    )

  import play.api.libs.json._
  override def migrations = Map(
    JsonMigrations.transform[MigratedEvent](
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
    JsonMigrations.renamed("event1.old.ClassName", inVersion = 2, toClass = classOf[Event1]),
    JsonMigrations.renamed("MigratedEvent.old.ClassName", inVersion = 2, toClass = classOf[MigratedEvent])
  )
}

object TestRegistry3 extends JsonSerializerRegistry {
  override def serializers = Seq(
    JsonSerializer(Json.format[MigratedEvent])
  )

  def currentMigrationVersion: Int = 5

  // manual way to do the same transformations (compared to json transformations above)
  override def migrations: Map[String, JsonMigration] = Map(
    classOf[MigratedEvent].getName -> new JsonMigration(currentMigrationVersion) {
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

object TestRegistryWithCompression extends JsonSerializerRegistry {

  implicit val innerFormat = Json.format[Inner]

  override def serializers: Seq[JsonSerializer[_]] =
    Seq(
      JsonSerializer.compressed[Event1],
      JsonSerializer.compressed(Json.format[Event2])
    )

}

object TestRegistryWithJson extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer(Json.format[Name])
  )
}

case class Box(surprise: Option[String])

class PlayJsonSerializerSpec extends WordSpec with Matchers {

  "The PlayJsonSerializer" should {

    "pick up serializers from configured registry" in withActorSystem(TestRegistry1) { system =>

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

    "pick up serializers for registry with migration" in withActorSystem(TestRegistry3) { system =>

      val migratedEvent = MigratedEvent(addedField = 2, newName = "some value")

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(migratedEvent).asInstanceOf[SerializerWithStringManifest]

      val bytes = serializer.toBinary(migratedEvent)
      val manifest = serializer.manifest(migratedEvent)

      bytes.isEmpty should be(false)

      val deserialized = serializer.fromBinary(bytes, manifest)
      deserialized should be(migratedEvent)

    }

    "format manifest of migrated type to include the version defined in migration registry" in withActorSystem(TestRegistry3) { system =>

      val migratedEvent = MigratedEvent(addedField = 2, newName = "some value")

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(migratedEvent).asInstanceOf[SerializerWithStringManifest]

      val manifest = serializer.manifest(migratedEvent)

      manifest shouldEqual expectedVersionedManifest(classOf[MigratedEvent], TestRegistry3.currentMigrationVersion)

    }

    "throw runtime exception when deserialization target type version is ahead of that defined in migration registry" in withActorSystem(TestRegistry3) { system =>

      val migratedEvent = MigratedEvent(addedField = 2, newName = "some value")

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(migratedEvent).asInstanceOf[SerializerWithStringManifest]

      val illegalVersion = TestRegistry3.currentMigrationVersion + 1
      val illegalManifest = expectedVersionedManifest(classOf[MigratedEvent], illegalVersion)

      assertThrows[IllegalStateException] {
        serializer.fromBinary(Array[Byte](), illegalManifest)
      }

    }

    "apply sequential migrations using json-transformations" in withActorSystem(TestRegistry2) { system =>

      val expectedEvent = MigratedEvent(addedField = 2, newName = "some value")
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "removedField" -> JsString("doesn't matter"),
        "oldName" -> JsString("some value")
      ))).getBytes(StandardCharsets.UTF_8)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]

      val oldVersionBeforeThanCurrentMigration = 1
      val manifest = expectedVersionedManifest(classOf[MigratedEvent], oldVersionBeforeThanCurrentMigration)
      val deserialized = serializer.fromBinary(oldJsonBytes, manifest)
      deserialized should be(expectedEvent)

    }

    "apply migrations written imperatively" in withActorSystem(TestRegistry3) { system =>

      val expectedEvent = MigratedEvent(addedField = 2, newName = "some value")
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "removedField" -> JsString("doesn't matter"),
        "oldName" -> JsString("some value")
      ))).getBytes(StandardCharsets.UTF_8)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]

      val oldVersionBeforeThanCurrentMigration = 1
      val manifest = expectedVersionedManifest(classOf[MigratedEvent], oldVersionBeforeThanCurrentMigration)
      val deserialized = serializer.fromBinary(oldJsonBytes, manifest)
      deserialized should be(expectedEvent)

    }

    "apply rename migration" in withActorSystem(TestRegistry2) { system =>

      val event = Event1("something", 25)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]

      val bytes = serializer.toBinary(event)
      val deserialized = serializer.fromBinary(bytes, "event1.old.ClassName")

      deserialized should be(event)

    }

    "apply rename migration and then use new name to apply migration transformations" in withActorSystem(TestRegistry2) { system =>

      val expectedEvent = MigratedEvent(addedField = 2, newName = "some value")
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "removedField" -> JsString("doesn't matter"),
        "oldName" -> JsString("some value")
      ))).getBytes(StandardCharsets.UTF_8)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]

      val deserialized = serializer.fromBinary(oldJsonBytes, "MigratedEvent.old.ClassName")

      deserialized should be(expectedEvent)
    }

    "use compression when enabled and payload is bigger than threshold" in withActorSystem(TestRegistryWithCompression) { system =>

      val serializeExt = SerializationExtension(system)
      val longContent = "test" * 1024
      val bigEvent1 = Event1(longContent, 1)
      val bigEvent2 = Event2(longContent, Inner(on = true))
      val shortContent = "clearText-short"
      val smallEvent1 = Event1(shortContent, 1)
      val smallEvent2 = Event2(shortContent, Inner(on = true))
      List(bigEvent1, bigEvent2).foreach { event =>
        val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]

        val bytes = serializer.toBinary(event)
        bytes.length should be < 1024 // this is a magic number. I know longContent will compress well under this 1024.
        Compression.isGZipped(bytes) should be(true)
        val manifest = serializer.manifest(event)

        bytes.isEmpty should be(false)

        val deserialized = serializer.fromBinary(bytes, manifest)
        deserialized should be(event)
      }
      List(smallEvent1, smallEvent2).foreach { event =>
        val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]

        val bytes = serializer.toBinary(event)
        new String(bytes) should include(shortContent)
        val manifest = serializer.manifest(event)

        bytes.isEmpty should be(false)

        val deserialized = serializer.fromBinary(bytes, manifest)
        deserialized should be(event)
      }
    }

    def expectedVersionedManifest[T](clazz: Class[T], migrationVersion: Int) = {
      s"${clazz.getName}#$migrationVersion"
    }

  }

  "The provided serializers" should {

    object Singleton

    "serialize and deserialize singletons" in {
      val serializer = JsonSerializer.emptySingletonFormat(Singleton)

      val result = serializer.reads(serializer.writes(Singleton))
      result.isSuccess shouldBe true
      result.get shouldBe Singleton
    }

    "serialize and deserialize JsValues" in withActorSystem(TestRegistryWithJson) { system =>
      val event = Name("hello, world")

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]
      val manifest = serializer.manifest(event)
      val bytes = serializer.toBinary(event)

      val res = serializer.fromBinary(bytes, manifest)
      assert(res == event)
    }

  }

  private var counter = 0
  def withActorSystem(registry: JsonSerializerRegistry)(test: ActorSystem => Unit): Unit = {
    var system: ActorSystem = null
    try {
      counter += 1
      system = ActorSystem(s"PlayJsonSerializerSpec-$counter", JsonSerializerRegistry.actorSystemSetupFor(registry))
      test(system)
    } finally {
      if (system ne null) TestKit.shutdownActorSystem(system)
    }
  }

}
