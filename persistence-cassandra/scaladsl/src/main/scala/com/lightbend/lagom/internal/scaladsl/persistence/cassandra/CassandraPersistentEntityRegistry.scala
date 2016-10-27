/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.stream.scaladsl
import com.lightbend.lagom.internal.scaladsl.persistence.{ AbstractPersistentEntityRegistry, PersistentEntityActor }
import com.lightbend.lagom.scaladsl.persistence._

/**
 * Internal API
 */
private[lagom] final class CassandraPersistentEntityRegistry(system: ActorSystem)
  extends AbstractPersistentEntityRegistry(system) {

  override protected val journalId = CassandraReadJournal.Identifier

  private val cassandraReadJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](journalId)

  override protected val eventsByTagQuery: Option[EventsByTagQuery] = Some(cassandraReadJournal)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): scaladsl.Source[EventStreamElement[Event], NotUsed] = {
    val tag = aggregateTag.tag
    val offset = fromOffset match {
      case NoOffset            => cassandraReadJournal.firstOffset
      case TimeBasedUUID(uuid) => uuid
      case other               => throw new IllegalArgumentException("Cassandra does not support " + other.getClass.getName + " offsets")
    }
    cassandraReadJournal.eventsByTag(tag, offset).map { env =>
      new EventStreamElement(PersistentEntityActor.extractEntityId(env.persistenceId), env.event.asInstanceOf[Event], TimeBasedUUID(env.offset))
    }
  }

}
