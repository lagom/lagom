/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json._

import scala.collection.immutable.Seq
import scala.reflect.ClassTag

object Serializers {

  /**
   * Implicitly provided serializers
   */
  object Implicits {
    implicit def optionFormat[A](implicit innerFormat: Format[A]): Format[Option[A]] =
      Serializers.optionFormat[A](innerFormat)
  }

  /**
   * Format that will represent optional fields in JSON as the serialized value for their type
   * when present and `null` when missing. Import from `Serializers.Implicits` when needed in
   * a macro generated format
   */
  def optionFormat[A](implicit innerFormat: Format[A]): Format[Option[A]] = Format[Option[A]](
    Reads[Option[A]] {
      case JsNull => JsSuccess(None)
      case other  => innerFormat.reads(other).map[Option[A]](Some.apply)
    },
    Writes[Option[A]] {
      case None    => JsNull
      case Some(a) => innerFormat.writes(a)
    }
  )

  /**
   * Creates a format that will serialize and deserialize a singleton to an empty js object
   */
  def emptySingletonFormat[A](singleton: A) = Format[A](
    Reads[A](_ => JsSuccess(singleton)),
    Writes[A](_ => JsObject(Seq.empty))
  )

  /**
   * Create a serializer for the PlayJsonSerializationRegistry, describes how a specific class can be read and written
   * as json using separate play-json [[Reads]] and [[Writes]]
   */
  def apply[T: ClassTag](reads: Reads[T], writes: Writes[T]): Serializers[T] =
    SerializersImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], reads, writes)

  /**
   * Create a serializer for the PlayJsonSerializationRegistry, describes how a specific class can be read and written
   * as json using separate play-json [[Reads]] and [[Writes]]
   */
  def apply[T: ClassTag](format: Format[T]): Serializers[T] =
    SerializersImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], format, format)

  private case class SerializersImpl[T](entityClass: Class[T], reads: Reads[T], writes: Writes[T]) extends Serializers[T]
}

/**
 * Describes how to serialize and deserialize a type using play-json
 */
sealed trait Serializers[T] {
  def entityClass: Class[T]
  def reads: Reads[T]
  def writes: Writes[T]
}

