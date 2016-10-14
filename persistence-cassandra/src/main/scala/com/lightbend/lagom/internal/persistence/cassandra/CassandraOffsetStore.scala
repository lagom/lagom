/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.util.Optional
import javax.inject.{ Inject, Singleton }

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import com.datastax.driver.core.{ BoundStatement, PreparedStatement, Row }
import com.lightbend.lagom.internal.javadsl.persistence.{ OffsetDao, OffsetStore, ReadSideConfig }
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession

import scala.concurrent.{ ExecutionContext, Future }
import scala.compat.java8.FutureConverters._

@Singleton
private[lagom] class CassandraOffsetStore @Inject() (system: ActorSystem, session: CassandraSession, config: ReadSideConfig)(implicit ec: ExecutionContext) extends OffsetStore {

  private val startupTask = ClusterStartupTask(
    system,
    "cassandraOffsetStorePrepare",
    createTable,
    config.globalPrepareTimeout,
    config.role,
    config.minBackoff,
    config.maxBackoff,
    config.randomBackoffFactor
  )

  private def createTable(): Future[Done] = {
    session.executeCreateTable(s"""
                                  |CREATE TABLE IF NOT EXISTS offsetStore (
                                  |  eventProcessorId text, tag text, timeUuidOffset timeuuid, sequenceOffset bigint,
                                  |  PRIMARY KEY (eventProcessorId, tag)
                                  |)""".stripMargin).toScala
  }

  /**
   * Prepare this offset store to process the given ID and tag.
   *
   * @param eventProcessorId The ID of the event processor.
   * @param tag              The tag to prepare for.
   * @return The DAO, with the loaded offset.
   */
  override def prepare(eventProcessorId: String, tag: String): Future[CassandraOffsetDao] = {
    implicit val timeout = Timeout(config.globalPrepareTimeout)
    for {
      _ <- startupTask.askExecute()
      offset <- readOffset(eventProcessorId, tag)
      statement <- prepareWriteOffset
    } yield {
      new CassandraOffsetDao(session, statement, eventProcessorId, tag, offset)
    }
  }

  private def prepareWriteOffset: Future[PreparedStatement] = {
    session.prepare("INSERT INTO offsetStore (eventProcessorId, tag, timeUuidOffset, sequenceOffset) VALUES (?, ?, ?, ?)").toScala
  }

  private def readOffset(eventProcessorId: String, tag: String): Future[Offset] = {
    session.selectOne(
      s"SELECT timeUuidOffset, sequenceOffset FROM offsetStore WHERE eventProcessorId = ? AND tag = ?",
      eventProcessorId, tag
    ).toScala.map(extractOffset)
  }

  private def extractOffset(maybeRow: Optional[Row]): Offset = {
    if (maybeRow.isPresent) {
      val row = maybeRow.get()
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
    } else Offset.NONE
  }

  private def writeOffset(statement: PreparedStatement, eventProcessorId: String, tag: String, offset: Offset): BoundStatement = {
    offset match {
      case Offset.NONE                => statement.bind(eventProcessorId, tag, null, null)
      case seq: Offset.Sequence       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq.value()))
      case uuid: Offset.TimeBasedUUID => statement.bind(eventProcessorId, tag, uuid.value(), null)
    }
  }
}

class CassandraOffsetDao(session: CassandraSession, statement: PreparedStatement, eventProcessorId: String, tag: String,
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
