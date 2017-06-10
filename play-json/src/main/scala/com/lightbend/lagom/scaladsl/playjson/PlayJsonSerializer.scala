/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import java.nio.charset.StandardCharsets

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.serialization.{ BaseSerializer, SerializerWithStringManifest }
import play.api.libs.json._

/**
 * Internal API
 *
 * Akka serializer using the registered play-json serializers and migrations
 */
private[lagom] final class PlayJsonSerializer(
  val system: ExtendedActorSystem,
  registry:   JsonSerializerRegistry
)
  extends SerializerWithStringManifest
  with BaseSerializer {

  private type Manifest = (String, Int, String, Int)
  private val charset = StandardCharsets.UTF_8
  private val log = Logging.getLogger(system, getClass)
  private val isDebugEnabled = log.isDebugEnabled

  private def migrations: Map[String, JsonMigration] = registry.migrations

  override def manifest(o: AnyRef): String = {
    serializeManifest(createManifest(o))
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val startTime = if (isDebugEnabled) System.nanoTime else 0L

    val (registeredClassName, _, _, _) = createManifest(o)

    // safe because the call to manifest already confirms that this serializer exists with
    // the returned registered class name
    val format = registry.formatFor(registeredClassName)

    val json = format.writes(o)
    val result = Json.stringify(json).getBytes(charset)

    if (isDebugEnabled) {
      val durationMicros = (System.nanoTime - startTime) / 1000

      log.debug(
        "Serialization of [{}] took [{}] µs, size [{}] bytes",
        o.getClass.getName, durationMicros, result.length
      )
    }
    result
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {

    val startTime = if (isDebugEnabled) System.nanoTime else 0L

    val (registeredClassName, registeredClassNameFromVersion, actualClassName, actualClassNameFromVersion) = parseManifest(manifest)

    val migratedActualClassName = {
      val renameMigration = migrations.get(actualClassName)
      renameMigration match {
        case Some(migration) if migration.currentVersion > actualClassNameFromVersion =>
          migration.transformClassName(actualClassNameFromVersion, actualClassName)
        case Some(migration) if migration.currentVersion < actualClassNameFromVersion =>
          throw new IllegalStateException(s"Migration version ${migration.currentVersion} is " +
            s"behind version $actualClassNameFromVersion of deserialized type [$actualClassName]")
        case _ => actualClassName
      }
    }

    val migratedRegisteredClassName = {
      val writerForClassNameMigration = migrations.get(registeredClassName)
      writerForClassNameMigration match {
        case Some(m) if m.currentVersion > registeredClassNameFromVersion =>
          m.transformClassName(registeredClassNameFromVersion, registeredClassName)
        case Some(m) if m.currentVersion < registeredClassNameFromVersion =>
          throw new IllegalStateException(s"Migration version ${m.currentVersion} is " +
            s"behind version $registeredClassNameFromVersion of deserialized type [$registeredClassNameFromVersion]")
        case _ => registeredClassName
      }
    }

    val json = Json.parse(bytes) match {
      case jsObject: JsObject => jsObject
      case other =>
        throw new RuntimeException("Unexpected serialized json data. " +
          s"Expected a JSON object, but was [${other.getClass.getName}]")
    }

    val transformMigration = migrations.get(migratedActualClassName)

    val migratedJson = transformMigration match {
      case Some(m) if m.currentVersion > actualClassNameFromVersion =>
        m.transform(actualClassNameFromVersion, json)
      case _ => json
    }

    val format = registry.formatFor(migratedRegisteredClassName)

    val result = format.reads(migratedJson) match {
      case JsSuccess(obj, _) => obj
      case JsError(errors) =>
        throw new JsonSerializationFailed(
          s"Failed to de-serialize bytes with manifest [$manifest]",
          errors,
          migratedJson
        )
    }

    if (isDebugEnabled) {
      val durationMicros = (System.nanoTime - startTime) / 1000

      log.debug(
        "Deserialization of [{}] took [{}] µs, size [{}] bytes",
        manifest, durationMicros, bytes.length
      )
    }
    result
  }

  private def serializeManifest(manifest: Manifest) = manifest match {
    case (registeredClassName, registeredClassNameFromVersion, actualClassName, actualClassNameFromVersion) =>
      s"$registeredClassName#$registeredClassNameFromVersion#$actualClassName#$actualClassNameFromVersion"
  }

  private def createManifest(o: AnyRef): Manifest = {
    registry
      .serializers
      .find(_.entityClass.isAssignableFrom(o.getClass))
      .map { serializer =>

        val registeredClassName = serializer.entityClass.getName
        val registeredClassVersion = migrations.get(registeredClassName) match {
          case Some(migration) => migration.currentVersion
          case None            => 1
        }

        val actualClassName = o.getClass.getName
        val actualClassNameVersion = migrations.get(actualClassName) match {
          case Some(migration) => migration.currentVersion
          case None            => 1
        }

        (registeredClassName, registeredClassVersion, actualClassName, actualClassNameVersion)
      }
      .getOrElse(throw new RuntimeException(s"Could not create manifest: Missing play-json serializer for [${o.getClass.getName}], " +
        s"defined are [${registry.registry.keys.mkString(", ")}]"))
  }

  private def parseManifest(manifest: String): Manifest = {
    val parts = manifest.split("#")

    parts.size match {
      case 4 => // updated format for supporting serialization of subclasses
        val registeredClassName = parts(0)
        val registeredClassNameFromVersion = parts(1)
        val actualClassName = parts(2)
        val actualClassNameFromVersion = parts(3)
        (registeredClassName, registeredClassNameFromVersion.toInt, actualClassName, actualClassNameFromVersion.toInt)

      case 2 => // for backwards compatibility when version was specified
        val actualClassName = parts(0)
        val fromVersion = parts(1)
        (actualClassName, fromVersion.toInt, actualClassName, fromVersion.toInt)

      case 1 => // for backwards compatibility when no version was specified
        (manifest, 1, manifest, 1)
    }
  }
}
