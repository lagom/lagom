/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.jackson

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import scala.annotation.tailrec
import scala.util.Failure
import scala.util.Success
import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.serialization.BaseSerializer
import akka.serialization.SerializerWithStringManifest
import com.lightbend.lagom.serialization.CompressedJsonable
import com.lightbend.lagom.serialization.JacksonJsonMigration

/**
 * Akka serializer for JSON using Jackson for classes that implements the
 * [[com.lightbend.lagom.serialization.Jsonable]] marker interface.
 *
 * Configuration in `lagom.serialization.json` section.
 * It will load Jackson modules defined in configuration `jackson-modules`.
 *
 * It will compress the payload if the message class implements the
 * [[com.lightbend.lagom.serialization.CompressedJsonable]] marker
 * interface and the payload is larger than the configured `compress-larger-than`
 * value.
 */
private[lagom] class JacksonJsonSerializer(val system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  private val log = Logging.getLogger(system, getClass)
  private val conf = system.settings.config.getConfig("lagom.serialization.json")
  private val isDebugEnabled = log.isDebugEnabled
  private val objectMapper = JacksonObjectMapperProvider(system).objectMapper
  private final val BufferSize = 1024 * 4
  private val migrations: Map[String, JacksonJsonMigration] = {
    import scala.collection.JavaConverters._
    conf.getConfig("migrations").root.unwrapped.asScala.toMap.map {
      case (k, v) ⇒
        val transformer = system.dynamicAccess.createInstanceFor[JacksonJsonMigration](v.toString, Nil).get
        k -> transformer
    }(collection.breakOut)
  }

  private val compressLargerThan: Long = conf.getBytes("compress-larger-than")

  override def manifest(obj: AnyRef): String = {
    val className = obj.getClass.getName
    migrations.get(className) match {
      case Some(transformer) => className + "#" + transformer.currentVersion
      case None              => className
    }

  }

  override def toBinary(obj: AnyRef): Array[Byte] = {
    val startTime = if (isDebugEnabled) System.nanoTime else 0L
    val bytes = objectMapper.writeValueAsBytes(obj)
    val result = obj match {
      case _: CompressedJsonable if bytes.length > compressLargerThan => compress(bytes)
      case _ => bytes
    }

    if (isDebugEnabled) {
      val durationMicros = (System.nanoTime - startTime) / 1000
      if (bytes.length == result.length)
        log.debug(
          "Serialization of [{}] took [{}] µs, size [{}] bytes",
          obj.getClass.getName, durationMicros, result.length
        )
      else
        log.debug(
          "Serialization of [{}] took [{}] µs, compressed size [{}] bytes, uncompressed size [{}] bytes",
          obj.getClass.getName, durationMicros, result.length, bytes.length
        )
    }

    result
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val startTime = if (isDebugEnabled) System.nanoTime else 0L
    val compressed = isGZipped(bytes)

    val migration = migrations.get(manifest)

    val i = manifest.lastIndexOf('#')
    val fromVersion = if (i == -1) 1 else manifest.substring(i + 1).toInt
    val manifestClassName = if (i == -1) manifest else manifest.substring(0, i)
    val className = migration match {
      case Some(transformer) if fromVersion < transformer.currentVersion =>
        transformer.transformClassName(fromVersion, manifestClassName)
      case _ => manifestClassName
    }

    val clazz = system.dynamicAccess.getClassFor[AnyRef](className) match {
      case Success(clazz) ⇒ clazz
      case Failure(e) ⇒
        throw new NotSerializableException(
          s"Cannot find manifest class [$className] for serializer [${getClass.getName}]."
        )
    }

    val decompressBytes = if (compressed) decompress(bytes) else bytes

    if (isDebugEnabled) {
      val durationMicros = (System.nanoTime - startTime) / 1000
      if (bytes.length == decompressBytes.length)
        log.debug(
          "Deserialization of [{}] took [{}] µs, size [{}] bytes",
          clazz.getName, durationMicros, decompressBytes.length
        )
      else
        log.debug(
          "Deserialization of [{}] took [{}] µs, compressed size [{}] bytes, uncompressed size [{}] bytes",
          clazz.getName, durationMicros, decompressBytes.length, bytes.length
        )
    }

    migration match {
      case Some(transformer) if fromVersion < transformer.currentVersion =>
        val jsonTree = objectMapper.readTree(decompressBytes)
        val newJsonTree = transformer.transform(fromVersion, jsonTree)
        objectMapper.treeToValue(newJsonTree, clazz)
      case _ =>
        objectMapper.readValue(decompressBytes, clazz)
    }
  }

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
