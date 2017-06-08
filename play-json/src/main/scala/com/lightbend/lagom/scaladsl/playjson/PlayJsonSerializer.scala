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
private[lagom] final class PlayJsonSerializer(
  val system: ExtendedActorSystem,
  writerFor:  Class[_],
  registry:   JsonSerializerRegistry
)
  extends SerializerWithStringManifest
  with BaseSerializer {

  private val writes: Writes[AnyRef] = registry.writesFor(writerFor)
  private val writerForClassName = writerFor.getName
  private val charset = StandardCharsets.UTF_8
  private val log = Logging.getLogger(system, getClass)
  private val isDebugEnabled = log.isDebugEnabled

  private def migrations: Map[String, JsonMigration] = registry.migrations

  override def manifest(o: AnyRef): String = {
    val className = o.getClass.getName
    migrations.get(className) match {
      case Some(migration) => writerForClassName + "#" + className + "#" + migration.currentVersion
      case None            => writerForClassName + "#" + className + "#1"
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val startTime = if (isDebugEnabled) System.nanoTime else 0L

    val json = writes.writes(o)
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

    val (fromVersion, originalWriterForClassName, manifestClassName) = parseManifest(manifest)

    val migration = migrations.get(manifestClassName)

    val (migratedOriginalWriterFor, migratedManifest) = migration match {
      case Some(m) if m.currentVersion > fromVersion =>
        val owf = m.transformClassName(fromVersion, originalWriterForClassName)
        val mm = m.transformClassName(fromVersion, manifestClassName)
        (owf, mm)
      case Some(m) if m.currentVersion < fromVersion =>
        throw new IllegalStateException(s"Migration version ${m.currentVersion} is " +
          s"behind version $fromVersion of deserialized type [$manifestClassName]")
      case _ => (originalWriterForClassName, manifestClassName)
    }

    val json = Json.parse(bytes) match {
      case jsObject: JsObject => jsObject
      case other =>
        throw new RuntimeException("Unexpected serialized json data. " +
          s"Expected a JSON object, but was [${other.getClass.getName}]")
    }

    val migratedJson = migration match {
      case Some(m) if m.currentVersion > fromVersion =>
        m.transform(fromVersion, json)
      case _ => json
    }

    val reads = registry.readsFor(migratedOriginalWriterFor)

    val result = reads.reads(migratedJson) match {
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

  private def parseManifest(manifest: String): (Int, String, String) = {
    val parts = manifest.split("#")

    parts.size match {
      case 3 => // updated format for supporting serialization of subclasses
        val originalWriterForClassName = parts(0)
        val manifestClassName = parts(1)
        val fromVersion = parts(2)
        (fromVersion.toInt, originalWriterForClassName, manifestClassName)

      case 2 => // for backwards compatibility when version was specified
        val manifestClassName = parts(0)
        val fromVersion = parts(1)
        (fromVersion.toInt, manifestClassName, manifestClassName)

      case 1 => // for backwards compatibility when no version was specified
        (1, manifest, manifest)
    }
  }
}
