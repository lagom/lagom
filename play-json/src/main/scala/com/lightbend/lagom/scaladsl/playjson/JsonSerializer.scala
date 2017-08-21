/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json._

import scala.collection.immutable.Seq
import scala.reflect.ClassTag

object JsonSerializer {

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
  def apply[T: ClassTag: Format]: JsonSerializer[T] =
    JsonSerializerImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], implicitly[Format[T]])

  /**
   * Create a serializer for the PlayJsonSerializationRegistry, describes how a specific class can be read and written
   * as json using separate play-json [[Reads]] and [[Writes]]
   */
  def apply[T: ClassTag](format: Format[T]): JsonSerializer[T] =
    JsonSerializerImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], format)

  /**
   * Create a serializer for the PlayJsonSerializationRegistry that will apply a GZIP compression when the generated
   * JSON content is larger than <code>compress-larger-than</code> bytes, describes how a specific class can be read
   * and written as json using separate play-json [[Reads]] and [[Writes]].
   */
  def compressed[T: ClassTag: Format]: JsonSerializer[T] =
    CompressedJsonSerializerImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], implicitly[Format[T]])

  /**
   * Create a serializer for the PlayJsonSerializationRegistry that will apply a GZIP compression when the generated
   * JSON content is larger than <code>compress-larger-than</code> bytes, describes how a specific class can be read
   * and written as json using separate play-json [[Reads]] and [[Writes]].
   */
  def compressed[T: ClassTag](format: Format[T]): JsonSerializer[T] =
    CompressedJsonSerializerImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], format)

  private[lagom] case class JsonSerializerImpl[T](entityClass: Class[T], format: Format[T]) extends JsonSerializer[T]
  private[lagom] case class CompressedJsonSerializerImpl[T](entityClass: Class[T], format: Format[T]) extends JsonSerializer[T]
}

/**
 * Describes how to serialize and deserialize a type using play-json
 */
sealed trait JsonSerializer[T] {
  // the reason we need it over Format is to capture the type here
  def entityClass: Class[T]
  def format: Format[T]
}
