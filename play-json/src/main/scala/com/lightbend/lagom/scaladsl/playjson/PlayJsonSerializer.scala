/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import java.nio.charset.StandardCharsets

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.serialization.{ BaseSerializer, SerializerWithStringManifest }
import play.api.libs.json._

import scala.collection.immutable

/**
 * Internal API
 *
 * Akka serializer using the registered play-json serializers and migrations
 */
private[lagom] final class PlayJsonSerializer(val system: ExtendedActorSystem, registry: JsonSerializerRegistry)
  extends SerializerWithStringManifest
  with BaseSerializer {

  private val charset = StandardCharsets.UTF_8
  private val log = Logging.getLogger(system, getClass)
  private val isDebugEnabled = log.isDebugEnabled

  private val serializers: Map[String, Format[AnyRef]] = {
    registry.serializers.map(entry =>
      (entry.entityClass.getName, entry.format.asInstanceOf[Format[AnyRef]])).toMap
  }

  private def migrations: Map[String, JsonMigration] = registry.migrations

  override def manifest(o: AnyRef): String = {
    val className = o.getClass.getName
    migrations.get(className) match {
      case Some(migration) => className + "#" + migration.currentVersion
      case None            => className
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val startTime = if (isDebugEnabled) System.nanoTime else 0L

    val (_, manifestClassName: String) = parseManifest(manifest(o))

    val format = serializers.getOrElse(
      manifestClassName,
      throw new RuntimeException(s"Missing play-json serializer for [$manifestClassName]")
    )

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

    val (fromVersion: Int, manifestClassName: String) = parseManifest(manifest)

    val renameMigration = migrations.get(manifestClassName)

    val migratedManifest = renameMigration match {
      case Some(migration) if (migration.currentVersion > fromVersion) =>
        migration.transformClassName(fromVersion, manifestClassName)
      case Some(migration) if (migration.currentVersion < fromVersion) =>
        throw new IllegalStateException(s"Migration version ${migration.currentVersion} is " +
          s"behind version $fromVersion of deserialized type [$manifestClassName]")
      case _ => manifestClassName
    }

    val transformMigration = migrations.get(migratedManifest)

    val format = serializers.getOrElse(
      migratedManifest,
      throw new RuntimeException(s"Missing play-json serializer for [$migratedManifest], " +
        s"defined are [${serializers.keys.mkString(", ")}]")
    )

    val json = Json.parse(bytes) match {
      case jsObject: JsObject => jsObject
      case other =>
        throw new RuntimeException("Unexpected serialized json data. " +
          s"Expected a JSON object, but was [${other.getClass.getName}]")
    }

    val migratedJson = transformMigration match {
      case Some(migration) if migration.currentVersion > fromVersion =>
        migration.transform(fromVersion, json)
      case _ => json
    }

    val result = format.reads(migratedJson) match {
      case JsSuccess(obj, _) => obj
      case JsError(errors) =>
        throw new JsonSerializationFailed(
          s"Failed to de-serialize bytes with manifest [$migratedManifest]",
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

  private def parseManifest(manifest: String) = {
    val i = manifest.lastIndexOf('#')
    val fromVersion = if (i == -1) 1 else manifest.substring(i + 1).toInt
    val manifestClassName = if (i == -1) manifest else manifest.substring(0, i)
    (fromVersion, manifestClassName)
  }
}
