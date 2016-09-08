/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import akka.NotUsed
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.javadsl.Source

import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraReadSideImpl, CassandraSessionImpl }
import com.lightbend.lagom.javadsl.persistence.Offset.TimeBasedUUID

object CassandraReadSideSpec {

  val config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    """)

}

class CassandraReadSideSpec extends CassandraPersistenceSpec(CassandraReadSideSpec.config) with AbstractReadSideSpec {
  lazy val testSession: CassandraSession = new CassandraSessionImpl(system)
  lazy val queries = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): Source[akka.japi.Pair[Event, Offset], NotUsed] = {
    val tag = aggregateTag.tag
    val offset = fromOffset match {
      case Offset.NONE         => queries.firstOffset
      case uuid: TimeBasedUUID => uuid.value()
      case other               => throw new IllegalArgumentException("Cassandra does not support " + other.getClass.getName + " offsets")
    }
    queries.eventsByTag(tag, offset)
      .map { env => akka.japi.Pair.create(env.event.asInstanceOf[Event], Offset.timeBasedUUID(env.offset)) }
      .asJava
  }

  val readSide = new TestEntityReadSide(testSession)
  val cassandraReadSide = new CassandraReadSideImpl(system, testSession, null, null)

  override def getAppendCount(id: String) = readSide.getAppendCount(id)

  override def processorFactory(): ReadSideProcessor[TestEntity.Evt] = {
    new TestEntityReadSide.TestEntityReadSideProcessor(cassandraReadSide, testSession)
  }

}

