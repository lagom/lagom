/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json.{ Format, Reads, Writes }

import scala.reflect.ClassTag

object Serializers {

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

