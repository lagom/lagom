/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import java.nio.charset.StandardCharsets

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.serialization.SerializerWithStringManifest
import play.api.libs.json._

import scala.collection.immutable.Seq

/**
 * Internal API
 *
 * Akka serializer using the registered play-json serializers and provides
 */
final class PlayJsonSerializer(system: ExtendedActorSystem) extends SerializerWithStringManifest {

  private val charset = StandardCharsets.UTF_8
  private val log = Logging.getLogger(system, getClass)
  private val isDebugEnabled = log.isDebugEnabled

  val registry: Map[String, (Reads[AnyRef], Writes[AnyRef])] = {
    val registryClassName: String = system.settings.config.getString("lagom.serialization.play-json.serialization-registry")
    val registry =
      system.dynamicAccess.createInstanceFor[PlayJsonSerializationRegistry](registryClassName, Seq.empty).get

    registry.serializers.map(entry =>
      (entry.clazz.getSimpleName, (entry.reads.asInstanceOf[Reads[AnyRef]], entry.writes.asInstanceOf[Writes[AnyRef]]))).toMap
  }

  override val identifier: Int = 715827892

  override def manifest(o: AnyRef): String = o.getClass.getSimpleName

  override def toBinary(o: AnyRef): Array[Byte] = {
    val startTime = if (isDebugEnabled) System.nanoTime else 0L

    val key = manifest(o)
    val (_, writes) = registry.getOrElse(key, throw new RuntimeException(s"Missing play-json serializer for '$key'"))

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

    val (reads, _) = registry.getOrElse(manifest, throw new RuntimeException(s"Missing play-json serializer for $manifest"))

    val json = Json.parse(bytes)
    val result = reads.reads(json) match {
      case JsSuccess(obj, _) => obj // TODO hook in migrations
      case JsError(errors) =>
        // TODO better pretty print of errors
        throw new RuntimeException(s"Failed to serialize bytes with manifest $manifest, json errors: ${errors.mkString(", ")}")
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
}
