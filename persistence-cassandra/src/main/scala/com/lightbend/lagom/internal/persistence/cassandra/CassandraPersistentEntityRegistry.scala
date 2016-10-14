/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import javax.inject.{ Inject, Singleton }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.japi.Pair
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.stream.javadsl
import com.google.inject.Injector
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.Offset.TimeBasedUUID
import com.lightbend.lagom.javadsl.persistence._

@Singleton
private[lagom] class CassandraPersistentEntityRegistry @Inject() (system: ActorSystem, injector: Injector)
  extends AbstractPersistentEntityRegistry(system, injector) {

  override protected val journalId = CassandraReadJournal.Identifier

  private val cassandraReadJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](journalId)

  override protected val eventsByTagQuery: Option[EventsByTagQuery] = Some(cassandraReadJournal)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): javadsl.Source[Pair[Event, Offset], NotUsed] = {
    val tag = aggregateTag.tag
    val offset = fromOffset match {
      case Offset.NONE         => cassandraReadJournal.firstOffset
      case uuid: TimeBasedUUID => uuid.value()
      case other               => throw new IllegalArgumentException("Cassandra does not support " + other.getClass.getName + " offsets")
    }
    cassandraReadJournal.eventsByTag(tag, offset)
      .map { env => Pair.create(env.event.asInstanceOf[Event], Offset.timeBasedUUID(env.offset)) }
      .asJava
  }

}
