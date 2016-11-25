/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.akkaserializer

import java.util.Optional

import akka.actor.ExtendedActorSystem
import akka.serialization.{ BaseSerializer, SerializationExtension, Serializer, SerializerWithStringManifest }

/**
 * Base Serializer to support Optional<T> as a State or ReplyType on a PersistentEntity.
 */
class OptionalSerializer(val system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  private val separator = ':'

  private val emptyManifest: String = "E"

  // Must be lazy otherwise there's an infinite loop when loading the SerializationExtension
  lazy val serialization = SerializationExtension(system)

  def serializer(clazz: Class[_]): Serializer = serialization.serializerFor(clazz) // .asInstanceOf[SerializerWithStringManifest]

  override def manifest(obj: AnyRef): String = {
    val optional: Optional[AnyRef] = obj.asInstanceOf[Optional[AnyRef]]
    if (optional.isPresent) {
      val obj1 = optional.get()
      val fqcn = obj1.getClass.getCanonicalName
      val srlzr = serializer(obj1.getClass)

      if (srlzr.includeManifest) {
        val manifest = srlzr.asInstanceOf[SerializerWithStringManifest].manifest(obj1)
        s"P$separator$fqcn$separator$manifest"
      } else {
        s"P$separator$fqcn"
      }

    } else {
      emptyManifest
    }
  }

  override def toBinary(obj: AnyRef): Array[Byte] = {
    val optional: Optional[AnyRef] = obj.asInstanceOf[Optional[AnyRef]]
    if (optional.isPresent) {
      val obj1 = optional.get()
      serializer(obj1.getClass).toBinary(obj1)
    } else
      Array.emptyByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val splits: Array[String] = manifest.split(":")
    splits(0) match {
      case `emptyManifest` => Optional.empty()
      case _ => {
        val clazz = system.dynamicAccess.classLoader.loadClass(splits(1))
        val srlrz = serializer(clazz)
        if (srlrz.includeManifest) {
          Optional.of(srlrz.asInstanceOf[SerializerWithStringManifest].fromBinary(bytes, splits(2)))
        } else {
          Optional.of(srlrz.fromBinary(bytes, clazz))
        }
      }
    }
  }

}
