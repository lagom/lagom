/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import scala.concurrent.duration._
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.AggregateEvent

/**
 * At system startup all [[CassandraReadSideProcessor]] classes must be registered here
 * with [[com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry#register]].
 */
trait CassandraReadSide {

  /**
   * At system startup all [[CassandraReadSideProcessor]] classes must be registered
   * with this method.
   */
  def register[Event <: AggregateEvent[Event]](
    processorClass: Class[_ <: CassandraReadSideProcessor[Event]]
  ): Unit

}
