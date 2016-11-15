/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import scala.collection.immutable

/**
 * Create a concrete subclass of this and register using the `lagom.serialization.play-json.serialization-registry`
 * setting to have your play-json serializers picked up and used by Lagom.
 */
abstract class SerializerRegistry {

  def serializers: immutable.Seq[Serializers[_]]

  /**
   * A set of migrations keyed by the fully classified class name that the migration should be triggered for
   */
  def migrations: Map[String, Migration] = Map.empty

}

class EmptySerializerRegistry extends SerializerRegistry {
  override val serializers = Nil
}
