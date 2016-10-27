/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.cassandra

import javax.inject.{ Inject, Singleton }

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import com.datastax.driver.core.{ BoundStatement, PreparedStatement, Row }
import com.lightbend.lagom.internal.javadsl.persistence.{ OffsetDao, OffsetStore }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.AbstractCassandraOffsetStore
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Internal API
 */
@Singleton
private[lagom] final class CassandraOffsetStore @Inject() (system: ActorSystem, session: CassandraSession, config: ReadSideConfig)(implicit ec: ExecutionContext)
  extends AbstractCassandraOffsetStore(system, session.scalaDelegate, config) with OffsetStore {

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
          Offset.timeBasedUUID(uuid)
        } else {
          if (row.isNull("sequenceOffset")) {
            Offset.NONE
          } else {
            Offset.sequence(row.getLong("sequenceOffset"))
          }
        }
      case None => Offset.NONE
    }
  }

  protected def writeOffset(statement: PreparedStatement, eventProcessorId: String, tag: String, offset: DslOffset): BoundStatement = {
    offset match {
      case Offset.NONE                => statement.bind(eventProcessorId, tag, null, null)
      case seq: Offset.Sequence       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq.value()))
      case uuid: Offset.TimeBasedUUID => statement.bind(eventProcessorId, tag, uuid.value(), null)
    }
  }
}

/**
 * Internal API
 */
final class CassandraOffsetDao(session: CassandraSession, statement: PreparedStatement, eventProcessorId: String, tag: String,
                               override val loadedOffset: Offset) extends OffsetDao {
  override def saveOffset(offset: Offset): Future[Done] = {
    session.executeWrite(bindSaveOffset(offset)).toScala
  }
  def bindSaveOffset(offset: Offset): BoundStatement = {
    offset match {
      case Offset.NONE                => statement.bind(eventProcessorId, tag, null, null)
      case seq: Offset.Sequence       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq.value()))
      case uuid: Offset.TimeBasedUUID => statement.bind(eventProcessorId, tag, uuid.value(), null)
    }
  }
}
