/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.japi.Pair
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.javadsl.Source
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt

class JdbcReadSideSpec extends JdbcPersistenceSpec with AbstractReadSideSpec {

  lazy val readSide = new JdbcTestEntityReadSide(session)
  lazy val queries = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  override def eventStream[Event <: AggregateEvent[Event]](aggregateTag: AggregateEventTag[Event], fromOffset: Offset): Source[Pair[Event, Offset], NotUsed] = {
    val tag = aggregateTag.tag
    val offset = fromOffset match {
      case Offset.NONE          => 0l
      case seq: Offset.Sequence => seq.value() + 1
      case other                => throw new IllegalArgumentException(s"JDBC does not support ${other.getClass.getSimpleName} offsets")
    }
    queries.eventsByTag(tag, offset)
      .map { env => Pair.create(env.event.asInstanceOf[Event], Offset.sequence(env.offset)) }
      .asJava
  }

  override def processorFactory(): ReadSideProcessor[Evt] = {
    new JdbcTestEntityReadSide.TestEntityReadSideProcessor(jdbcReadSide)
  }

  override def getAppendCount(id: String): CompletionStage[java.lang.Long] = {
    readSide.getAppendCount(id)
  }
}
