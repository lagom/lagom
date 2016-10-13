/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json.{ Format, Reads, Writes }

object Serializers {

  def apply[T](clazz: Class[T], format: Format[T]): Serializers[T] =
    new Serializers(clazz, format, format)

}

/**
 * Entry for the PlayJsonSerializationRegistry, describes how a specific class can be read and written
 * as json using play-json
 */
final case class Serializers[T](clazz: Class[T], reads: Reads[T], writes: Writes[T])