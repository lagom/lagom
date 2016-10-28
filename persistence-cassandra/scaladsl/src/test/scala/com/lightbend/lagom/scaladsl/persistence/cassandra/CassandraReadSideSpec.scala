/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import akka.NotUsed
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.scaladsl.persistence.PersistentEntityActor
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ CassandraOffsetStore, CassandraReadSideImpl }
import com.lightbend.lagom.scaladsl.persistence._
import com.typesafe.config.ConfigFactory

object CassandraReadSideSpec {

  val config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    """)

}

class CassandraReadSideSpec extends CassandraPersistenceSpec(CassandraReadSideSpec.config) with AbstractReadSideSpec {
  lazy val testSession: CassandraSession = new CassandraSession(system)
  lazy val queries = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): Source[EventStreamElement[Event], NotUsed] = {
    val offset = fromOffset match {
      case NoOffset            => queries.firstOffset
      case TimeBasedUUID(uuid) => uuid
      case other               => throw new IllegalArgumentException("Cassandra does not support " + other.getClass.getName + " offsets")
    }
    queries.eventsByTag(aggregateTag.tag, offset)
      .map { env => new EventStreamElement[Event](PersistentEntityActor.extractEntityId(env.persistenceId), env.event.asInstanceOf[Event], TimeBasedUUID(env.offset)) }

  }

  val readSide = new TestEntityReadSide(system, testSession)
  val cassandraReadSide = new CassandraReadSideImpl(system, testSession, new CassandraOffsetStore(system, testSession,
    ReadSideConfig())(system.dispatcher))

  override def getAppendCount(id: String) = readSide.getAppendCount(id)

  override def processorFactory(): ReadSideProcessor[TestEntity.Evt] = {
    new TestEntityReadSide.TestEntityReadSideProcessor(system, cassandraReadSide, testSession)
  }

}

