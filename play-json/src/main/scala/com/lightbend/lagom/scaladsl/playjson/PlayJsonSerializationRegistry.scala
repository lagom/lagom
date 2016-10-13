/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json.{ Reads, Writes }
import scala.collection.immutable.Seq

object PlayJsonSerializationRegistry {
  object Entry {
    def apply[T, ReadsAndWrites <: Reads[T] with Writes[T]](clazz: Class[T], both: ReadsAndWrites): Entry[T] =
      Entry(clazz, both, both)
  }

  /**
   * Entry for the PlayJsonSerializationRegistry, describes how a specific class can be read and written
   * as json using play-json
   */
  case class Entry[T](clazz: Class[T], reads: Reads[T], writes: Writes[T])

}
/**
 * Something
 */
trait PlayJsonSerializationRegistry {

  def serializers: Seq[PlayJsonSerializationRegistry.Entry[_]]

  // TODO migrations
}
