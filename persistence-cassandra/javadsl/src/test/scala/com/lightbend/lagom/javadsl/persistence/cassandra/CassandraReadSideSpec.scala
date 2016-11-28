/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import akka.NotUsed
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.javadsl.Source
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.javadsl.persistence.cassandra.{ CassandraReadSideImpl, JavadslCassandraOffsetStore }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.javadsl.persistence._
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
  ): Source[akka.japi.Pair[Event, Offset], NotUsed] = {
    val tag = aggregateTag.tag
    queries.eventsByTag(tag, OffsetAdapter.dslOffsetToOffset(fromOffset))
      .map { env => akka.japi.Pair.create(env.event.asInstanceOf[Event], OffsetAdapter.offsetToDslOffset(env.offset)) }
      .asJava
  }

  val readSide = new TestEntityReadSide(testSession)
  val cassandraReadSide = new CassandraReadSideImpl(system, testSession, new JavadslCassandraOffsetStore(system, testSession,
    ReadSideConfig())(system.dispatcher), null, null)

  override def getAppendCount(id: String) = readSide.getAppendCount(id)

  override def processorFactory(): ReadSideProcessor[TestEntity.Evt] = {
    new TestEntityReadSide.TestEntityReadSideProcessor(cassandraReadSide, testSession)
  }

}

