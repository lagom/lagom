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

sealed trait GenericEvent
case class SpecificEvent1(x: Int) extends GenericEvent
case class SpecificEvent2(s: String) extends GenericEvent
case class MigratedSpecificEvent(addedField: Int, newName: String) extends GenericEvent
case class UnrelatedEvent(y: Boolean)

object GenericEvent {

  // In practice all of this could be generated via derived codecs: https://github.com/julienrf/play-json-derived-codecs
  private val specificEvent1Format = Json.format[SpecificEvent1]
  private val specificEvent2Format = Json.format[SpecificEvent2]
  private val migratedSpecificEventFormat = Json.format[MigratedSpecificEvent]

  implicit val format: Format[GenericEvent] = new Format[GenericEvent] {
    override def reads(json: JsValue): JsResult[GenericEvent] = (json \ "$type").asOpt[String] match {
      case Some(typeName) if typeName == SpecificEvent1.getClass.getName => specificEvent1Format.reads(json)
      case Some(typeName) if typeName == SpecificEvent2.getClass.getName => specificEvent2Format.reads(json)
      case Some(typeName) if typeName == MigratedSpecificEvent.getClass.getName => migratedSpecificEventFormat.reads(json)
      case _ => JsError()
    }

    override def writes(o: GenericEvent): JsValue = o match {
      case evt: SpecificEvent1 =>
        specificEvent1Format.writes(evt) ++ Json.obj("$type" -> SpecificEvent1.getClass.getName)
      case evt: SpecificEvent2 =>
        specificEvent2Format.writes(evt) ++ Json.obj("$type" -> SpecificEvent2.getClass.getName)
      case evt: MigratedSpecificEvent =>
        migratedSpecificEventFormat.writes(evt) ++ Json.obj("$type" -> MigratedSpecificEvent.getClass.getName)
    }
  }
}

object UnrelatedEvent {
  implicit val format: Format[UnrelatedEvent] = Json.format[UnrelatedEvent]
}

case class Event1(name: String, increment: Int)
object Event1 {
  implicit val format: Format[Event1] = Json.format[Event1]
}
case class Event2(name: String, inner: Inner)
case class Inner(on: Boolean)
case class MigratedEvent(addedField: Int, newName: String)

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
    JsonMigrations.renamed("event1.old.ClassName", inVersion = 2, toClass = classOf[Event1])
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

object TestRegistry4 extends JsonSerializerRegistry {
  override def serializers = Seq(
    JsonSerializer[GenericEvent],
    JsonSerializer[UnrelatedEvent]
  )

  override def migrations: Map[String, JsonMigration] = Map(
    JsonMigrations.transform[MigratedSpecificEvent](
      // something like a history of changes, if version is oldest each one needs to be applied
      SortedMap(
        // remove field (not really needed, here for completeness)
        1 -> (__ \ "removedField").json.prune,
        // add a field with some default value
        // update the "$type" to match the new type name
        2 -> __.json.update((__ \ "addedField").json.put(JsString("2"))).andThen(__.json.update((__ \ "$type").json.put(JsString(MigratedSpecificEvent.getClass.getName)))),
        // a field was renamed, this one is tricky -
        // copy value first, using "update", then remove the old key using "prune"
        3 -> __.json.update((__ \ "newName").json.copyFrom((__ \ "oldName").json.pick))
          .andThen((__ \ "oldName").json.prune),
        4 ->
          // a field changed type
          (__ \ "addedField").json.update(Format.of[JsString].map { case JsString(value) => JsNumber(value.toInt) })
      )
    ),
    JsonMigrations.renamed("MigratedSpecificEvent.old.ClassName", inVersion = 2, toClass = classOf[MigratedSpecificEvent]),
    JsonMigrations.renamed("GenericEvent.old.ClassName", inVersion = 3, toClass = classOf[GenericEvent])
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

      manifest shouldEqual expectedVersionedManifest(classOf[MigratedEvent], classOf[MigratedEvent], TestRegistry3.currentMigrationVersion)

    }

    "perform migration given previous manifest-with-version format" in withActorSystem(TestRegistry3) { system =>

      val expectedEvent = MigratedEvent(addedField = 2, newName = "some value")
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "removedField" -> JsString("doesn't matter"),
        "oldName" -> JsString("some value")
      ))).getBytes(StandardCharsets.UTF_8)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]

      val oldVersion = 1
      val oldManifestWithVersion = s"${expectedEvent.getClass.getName}#$oldVersion"

      val deserialized = serializer.fromBinary(oldJsonBytes, oldManifestWithVersion)

      deserialized should equal(expectedEvent)
    }

    "perform migration given previous manifest-without-version format" in withActorSystem(TestRegistry1) { system =>

      val expectedEvent = Event1("test", 1)
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "name" -> JsString("test"),
        "increment" -> JsNumber(1)
      ))).getBytes(StandardCharsets.UTF_8)

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]

      val oldManifestWithVersion = s"${expectedEvent.getClass.getName}"

      val deserialized = serializer.fromBinary(oldJsonBytes, oldManifestWithVersion)

      deserialized should equal(expectedEvent)
    }

    "throw runtime exception when deserialization target type version is ahead of that defined in migration registry" in withActorSystem(TestRegistry3) { system =>

      val migratedEvent = MigratedEvent(addedField = 2, newName = "some value")

      val serializeExt = SerializationExtension(system)
      val serializer = serializeExt.findSerializerFor(migratedEvent).asInstanceOf[SerializerWithStringManifest]

      val illegalVersion = TestRegistry3.currentMigrationVersion + 1
      val illegalManifest = expectedVersionedManifest(classOf[MigratedEvent], classOf[MigratedEvent], illegalVersion)

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
      val manifest = expectedVersionedManifest(classOf[MigratedEvent], classOf[MigratedEvent], oldVersionBeforeThanCurrentMigration)
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
      val manifest = expectedVersionedManifest(classOf[MigratedEvent], classOf[MigratedEvent], oldVersionBeforeThanCurrentMigration)
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

    "serialize covariant types with generic serializer" in withActorSystem(TestRegistry4) { system =>
      val serializeExt = SerializationExtension(system)

      List(SpecificEvent1(1), SpecificEvent2("test")).foreach { event =>
        val serializer = serializeExt.findSerializerFor(event).asInstanceOf[SerializerWithStringManifest]
        val bytes = serializer.toBinary(event)
        val manifest = serializer.manifest(event)
        val deserialized = serializer.fromBinary(bytes, manifest)
        deserialized should be(event)
      }
    }

    "migrate generic trait name, migrated event name, and run migration" in withActorSystem(TestRegistry4) { system =>
      val serializeExt = SerializationExtension(system)
      val expectedEvent = MigratedSpecificEvent(addedField = 2, newName = "some value")
      val oldJsonBytes = Json.stringify(JsObject(Seq(
        "removedField" -> JsString("doesn't matter"),
        "oldName" -> JsString("some value"),
        "$type" -> JsString("MigratedSpecificEvent.old.ClassNam")
      ))).getBytes(StandardCharsets.UTF_8)
      val serializer = serializeExt.findSerializerFor(expectedEvent).asInstanceOf[SerializerWithStringManifest]
      val oldVersion = 1
      val manifestV2 = s"GenericEvent.old.ClassName#MigratedSpecificEvent.old.ClassName#$oldVersion"
      val deserialized = serializer.fromBinary(oldJsonBytes, manifestV2)
      deserialized should be(expectedEvent)
    }

    "serialize with specific serializer and deserialize with any serializer" in withActorSystem(TestRegistry4) { system =>
      val serializeExt = SerializationExtension(system)
      val specificEvent = SpecificEvent1(x = 1)
      val unrelatedEvent = UnrelatedEvent(y = true)
      val genericEventSerializer = serializeExt.findSerializerFor(specificEvent).asInstanceOf[SerializerWithStringManifest]
      val unrelatedEventSerializer = serializeExt.findSerializerFor(unrelatedEvent).asInstanceOf[SerializerWithStringManifest]

      val specificEventBytes = genericEventSerializer.toBinary(specificEvent)
      val unrelatedEventBytes = unrelatedEventSerializer.toBinary(unrelatedEvent)

      val specificEventManifest = genericEventSerializer.manifest(specificEvent)
      val unrelatedEventManifest = unrelatedEventSerializer.manifest(unrelatedEvent)

      val specificEventDeserializedByGenericEventSerializer = genericEventSerializer.fromBinary(specificEventBytes, specificEventManifest)
      val unrelatedEventDeserializedByGenericEventSerializer = genericEventSerializer.fromBinary(unrelatedEventBytes, unrelatedEventManifest)
      val specificEventDeserializedByUnrelatedEventSerializer = unrelatedEventSerializer.fromBinary(specificEventBytes, specificEventManifest)
      val unrelatedEventDeserializedByUnrelatedEventSerializer = unrelatedEventSerializer.fromBinary(unrelatedEventBytes, unrelatedEventManifest)

      specificEventDeserializedByGenericEventSerializer should be(specificEvent)
      specificEventDeserializedByUnrelatedEventSerializer should be(specificEvent)
      unrelatedEventDeserializedByGenericEventSerializer should be(unrelatedEvent)
      unrelatedEventDeserializedByUnrelatedEventSerializer should be(unrelatedEvent)
    }

    def expectedVersionedManifest[A, B <: A](writerForClass: Class[A], clazz: Class[B], migrationVersion: Int) = {
      s"${writerForClass.getName}#${clazz.getName}#$migrationVersion"
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
