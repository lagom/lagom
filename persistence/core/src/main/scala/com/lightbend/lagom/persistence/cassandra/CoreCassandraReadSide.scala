/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.persistence.cassandra

import scala.concurrent.duration._
import com.lightbend.lagom.persistence.AggregateEventTag
import com.lightbend.lagom.persistence.AggregateEvent
import scala.reflect.ClassTag

/**
 * At system startup all [[CassandraReadSideProcessor]] classes must be registered here
 * with [[com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry#register]].
 */
trait CoreCassandraReadSide {

  /**
   * At system startup all [[CassandraReadSideProcessor]] classes must be registered
   * with this method.
   */
  def register[Event <: AggregateEvent[Event], Processor <: CoreCassandraReadSideProcessor[Event]: ClassTag](
    processorFactory: () => Processor
  ): Unit

}
