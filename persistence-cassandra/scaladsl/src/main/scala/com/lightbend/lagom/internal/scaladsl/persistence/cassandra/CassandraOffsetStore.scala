/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import com.datastax.driver.core.{ BoundStatement, PreparedStatement, Row }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.AbstractCassandraOffsetStore
import com.lightbend.lagom.internal.scaladsl.persistence.{ OffsetDao, OffsetStore }
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import com.lightbend.lagom.scaladsl.persistence.{ NoOffset, Offset, Sequence, TimeBasedUUID }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Internal API
 */
private[lagom] final class CassandraOffsetStore(system: ActorSystem, session: CassandraSession, config: ReadSideConfig)(implicit ec: ExecutionContext)
  extends AbstractCassandraOffsetStore(system, session.delegate, config) with OffsetStore {

  override type DslOffset = Offset

  /**
   * Prepare this offset store to process the given ID and tag.
   *
   * @param eventProcessorId The ID of the event processor.
   * @param tag              The tag to prepare for.
   * @return The DAO, with the loaded offset.
   */
  override def prepare(eventProcessorId: String, tag: String): Future[CassandraOffsetDao] = {
    implicit val timeout = Timeout(config.globalPrepareTimeout)
    doPrepare(eventProcessorId, tag).map {
      case (offset, statement) =>
        new CassandraOffsetDao(session, statement, eventProcessorId, tag, offset)
    }
  }

  protected def extractOffset(maybeRow: Option[Row]): DslOffset = {
    maybeRow match {
      case Some(row) =>
        val uuid = row.getUUID("timeUuidOffset")
        if (uuid != null) {
          TimeBasedUUID(uuid)
        } else {
          if (row.isNull("sequenceOffset")) {
            NoOffset
          } else {
            Sequence(row.getLong("sequenceOffset"))
          }
        }
      case None => NoOffset
    }
  }

  protected def writeOffset(statement: PreparedStatement, eventProcessorId: String, tag: String, offset: DslOffset): BoundStatement = {
    offset match {
      case NoOffset            => statement.bind(eventProcessorId, tag, null, null)
      case Sequence(seq)       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq))
      case TimeBasedUUID(uuid) => statement.bind(eventProcessorId, tag, uuid, null)
    }
  }
}

/**
 * Internal API
 */
final class CassandraOffsetDao(session: CassandraSession, statement: PreparedStatement, eventProcessorId: String, tag: String,
                               override val loadedOffset: Offset) extends OffsetDao {
  override def saveOffset(offset: Offset): Future[Done] = {
    session.executeWrite(bindSaveOffset(offset))
  }
  def bindSaveOffset(offset: Offset): BoundStatement = {
    offset match {
      case NoOffset            => statement.bind(eventProcessorId, tag, null, null)
      case Sequence(seq)       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq))
      case TimeBasedUUID(uuid) => statement.bind(eventProcessorId, tag, uuid, null)
    }
  }
}
