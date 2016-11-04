/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import akka.NotUsed
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{ NoOffset, Offset, PersistenceQuery, Sequence }
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.scaladsl.persistence.PersistentEntityActor
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.Future

class JdbcReadSideSpec extends JdbcPersistenceSpec with AbstractReadSideSpec {

  lazy val readSide = new JdbcTestEntityReadSide(session)
  lazy val queries = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  override def eventStream[Event <: AggregateEvent[Event]](aggregateTag: AggregateEventTag[Event], fromOffset: Offset): Source[EventStreamElement[Event], NotUsed] = {
    val tag = aggregateTag.tag
    val offset = fromOffset match {
      case NoOffset        => 0l
      case Sequence(value) => value + 1
      case other           => throw new IllegalArgumentException(s"JDBC does not support ${other.getClass.getSimpleName} offsets")
    }
    queries.eventsByTag(tag, offset)
      .map { env =>
        new EventStreamElement[Event](
          PersistentEntityActor.extractEntityId(env.persistenceId),
          env.event.asInstanceOf[Event],
          Sequence(env.offset): Offset
        )
      }
  }

  override def processorFactory(): ReadSideProcessor[Evt] = {
    new JdbcTestEntityReadSide.TestEntityReadSideProcessor(jdbcReadSide)
  }

  override def getAppendCount(id: String): Future[Long] = {
    readSide.getAppendCount(id)
  }
}
