/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.persistence.cassandra.CoreCassandraReadSideProcessor
import com.lightbend.lagom.persistence.AggregateEvent

/**
 * Consume events produced by [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]]
 * instances and update one or more tables in Cassandra that are optimized for queries.
 * The events belong to a [[com.lightbend.lagom.javadsl.persistence.AggregateEventTag]], e.g. all
 * persistent events of all `Order` entities.
 */
abstract class CassandraReadSideProcessor[Event <: AggregateEvent[Event]] extends CoreCassandraReadSideProcessor[Event]
