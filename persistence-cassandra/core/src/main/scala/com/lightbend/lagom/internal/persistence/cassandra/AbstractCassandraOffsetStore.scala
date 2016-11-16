/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.query.{ NoOffset, Offset, Sequence, TimeBasedUUID }
import akka.util.Timeout
import com.datastax.driver.core.{ BoundStatement, PreparedStatement, Row }
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.{ OffsetDao, ReadSideConfig }

import scala.concurrent.Future

/**
 * Internal API
 */
private[lagom] abstract class AbstractCassandraOffsetStore(
  system:  ActorSystem,
  session: akka.persistence.cassandra.session.scaladsl.CassandraSession,
  config:  ReadSideConfig
) {

  protected type DslOffset
  import system.dispatcher

  protected def offsetToDslOffset(offset: Offset): DslOffset
  protected def dslOffsetToOffset(dslOffset: DslOffset): Offset

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
      |)""".stripMargin)
  }

  /**
   * Prepare this offset store to process the given ID and tag.
   *
   * @param eventProcessorId The ID of the event processor.
   * @param tag              The tag to prepare for.
   * @return The DAO, with the loaded offset.
   */
  protected def doPrepare(eventProcessorId: String, tag: String): Future[(DslOffset, PreparedStatement)] = {
    implicit val timeout = Timeout(config.globalPrepareTimeout)
    for {
      _ <- startupTask.askExecute()
      offset <- readOffset(eventProcessorId, tag)
      statement <- prepareWriteOffset
    } yield {
      (offset, statement)
    }
  }

  private def prepareWriteOffset: Future[PreparedStatement] = {
    session.prepare("INSERT INTO offsetStore (eventProcessorId, tag, timeUuidOffset, sequenceOffset) VALUES (?, ?, ?, ?)")

  }

  private def readOffset(eventProcessorId: String, tag: String): Future[DslOffset] = {
    session.selectOne(
      s"SELECT timeUuidOffset, sequenceOffset FROM offsetStore WHERE eventProcessorId = ? AND tag = ?",
      eventProcessorId, tag
    ).map(extractOffset)
  }

  private def extractOffset(maybeRow: Option[Row]): DslOffset = {
    val offset = maybeRow match {
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
    offsetToDslOffset(offset)
  }

  final def writeOffset(statement: PreparedStatement, eventProcessorId: String, tag: String, dslOffset: DslOffset): BoundStatement = {
    val offset = dslOffsetToOffset(dslOffset)
    offset match {
      case NoOffset            => statement.bind(eventProcessorId, tag, null, null)
      case Sequence(seq)       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq))
      case TimeBasedUUID(uuid) => statement.bind(eventProcessorId, tag, uuid, null)
      case _                   => throw new IllegalArgumentException("Cassandra does not support " + offset.getClass.getName + " offsets")
    }
  }

  final class CassandraOffsetDao(statement: PreparedStatement, eventProcessorId: String, tag: String,
                                 override val loadedOffset: Offset) extends OffsetDao {

    override def saveOffset(offset: Offset): Future[Done] = {
      session.executeWrite(bindSaveOffset(offset))
    }
    def bindSaveOffset(offset: Offset): BoundStatement = {
      offset match {
        case NoOffset            => statement.bind(eventProcessorId, tag, null, null)
        case Sequence(seq)       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq))
        case TimeBasedUUID(uuid) => statement.bind(eventProcessorId, tag, uuid, null)
        case _                   => throw new IllegalArgumentException("Cassandra does not support " + offset.getClass.getName + " offsets")
      }
    }

  }

}
