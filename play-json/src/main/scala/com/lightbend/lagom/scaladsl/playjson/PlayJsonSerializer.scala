/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.nio.charset.StandardCharsets
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.serialization.{ BaseSerializer, SerializerWithStringManifest }
import play.api.libs.json._

import scala.annotation.tailrec
import scala.collection.immutable

/**
 * Internal API
 *
 * Akka serializer using the registered play-json serializers and migrations
 */
private[lagom] final class PlayJsonSerializer(val system: ExtendedActorSystem, registry: JsonSerializerRegistry)
  extends SerializerWithStringManifest
  with BaseSerializer {

  import Compression._

  private val charset = StandardCharsets.UTF_8
  private val log = Logging.getLogger(system, getClass)
  private val conf = system.settings.config.getConfig("lagom.serialization.json")
  private val isDebugEnabled = log.isDebugEnabled

  private val compressLargerThan: Long = conf.getBytes("compress-larger-than")

  /** maps a manifestClassName to a suitable play-json Format */
  private val formatters: Map[String, Format[AnyRef]] = {
    registry.serializers.map((entry: JsonSerializer[_]) =>
      (entry.entityClass.getName, entry.format.asInstanceOf[Format[AnyRef]])).toMap
  }

  /** maps a manifestClassName to the serializer provided by the user */
  private val serializers: Map[String, JsonSerializer[_]] = {
    registry.serializers.map {
      entry => entry.entityClass.getName -> entry
    }.toMap
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

    val format = formatters.getOrElse(
      manifestClassName,
      throw new RuntimeException(s"Missing play-json serializer for [$manifestClassName]")
    )

    val json = format.writes(o)
    val bytes: Array[Byte] = Json.stringify(json).getBytes(charset)

    val result = serializers(manifestClassName) match {
      case JsonSerializer.CompressedJsonSerializerImpl(_, _) if bytes.length > compressLargerThan => compress(bytes)
      case _ => bytes
    }

    if (isDebugEnabled) {
      val durationMicros = (System.nanoTime - startTime) / 1000

      log.debug(
        "Serialization of [{}] took [{}] µs, size [{}] bytes",
        o.getClass.getName, durationMicros, result.length
      )
    }
    result
  }

  override def fromBinary(storedBytes: Array[Byte], manifest: String): AnyRef = {
    val startTime = if (isDebugEnabled) System.nanoTime else 0L

    val (fromVersion: Int, manifestClassName: String) = parseManifest(manifest)

    val renameMigration = migrations.get(manifestClassName)

    val migratedManifest = renameMigration match {
      case Some(migration) if migration.currentVersion > fromVersion =>
        migration.transformClassName(fromVersion, manifestClassName)
      case Some(migration) if migration.currentVersion < fromVersion =>
        throw new IllegalStateException(s"Migration version ${migration.currentVersion} is " +
          s"behind version $fromVersion of deserialized type [$manifestClassName]")
      case _ => manifestClassName
    }

    val transformMigration = migrations.get(migratedManifest)

    val format = formatters.getOrElse(
      migratedManifest,
      throw new RuntimeException(s"Missing play-json serializer for [$migratedManifest], " +
        s"defined are [${formatters.keys.mkString(", ")}]")
    )

    val bytes =
      if (isGZipped(storedBytes))
        decompress(storedBytes)
      else
        storedBytes

    val json = Json.parse(bytes) match {
      case jsValue: JsValue => jsValue
      case other =>
        throw new RuntimeException("Unexpected serialized json data. " +
          s"Expected a JSON object, but was [${other.getClass.getName}]")
    }

    val migratedJson = (transformMigration, json) match {
      case (Some(migration), js: JsObject) if migration.currentVersion > fromVersion =>
        migration.transform(fromVersion, js)
      case (Some(migration), js: JsValue) if migration.currentVersion > fromVersion =>
        migration.transformValue(fromVersion, js)
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

// This code is copied from JacksonJsonSerializer
private[lagom] object Compression {
  private final val BufferSize = 1024 * 4

  def compress(bytes: Array[Byte]): Array[Byte] = {
    val bos = new ByteArrayOutputStream(BufferSize)
    val zip = new GZIPOutputStream(bos)
    try zip.write(bytes)
    finally zip.close()
    bos.toByteArray
  }

  def decompress(bytes: Array[Byte]): Array[Byte] = {
    val in = new GZIPInputStream(new ByteArrayInputStream(bytes))
    val out = new ByteArrayOutputStream()
    val buffer = new Array[Byte](BufferSize)

    @tailrec def readChunk(): Unit = in.read(buffer) match {
      case -1 ⇒ ()
      case n ⇒
        out.write(buffer, 0, n)
        readChunk()
    }

    try readChunk()
    finally in.close()
    out.toByteArray
  }

  def isGZipped(bytes: Array[Byte]): Boolean = {
    (bytes != null) && (bytes.length >= 2) &&
      (bytes(0) == GZIPInputStream.GZIP_MAGIC.toByte) &&
      (bytes(1) == (GZIPInputStream.GZIP_MAGIC >> 8).toByte)
  }
}
