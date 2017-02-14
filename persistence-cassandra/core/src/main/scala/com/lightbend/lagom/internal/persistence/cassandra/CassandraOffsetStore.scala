/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.cassandra.session.scaladsl.CassandraSession
import akka.persistence.query.{ NoOffset, Offset, Sequence, TimeBasedUUID }
import akka.util.Timeout
import com.datastax.driver.core.{ BoundStatement, PreparedStatement, Row }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.spi.persistence.{ OffsetDao, OffsetStore }

import scala.concurrent.Future

/**
 * Internal API
 */
private[lagom] abstract class CassandraOffsetStore(
  system:  ActorSystem,
  session: CassandraSession,
  config:  ReadSideConfig
) extends OffsetStore {

  import system.dispatcher

  override def prepare(eventProcessorId: String, tag: String): Future[CassandraOffsetDao] = {
    implicit val timeout = Timeout(config.globalPrepareTimeout)
    doPrepare(eventProcessorId, tag).map {
      case (offset, statement) =>
        new CassandraOffsetDao(session, statement, eventProcessorId, tag, offset)
    }
  }

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

  protected def doPrepare(eventProcessorId: String, tag: String): Future[(Offset, PreparedStatement)] = {
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

  private def readOffset(eventProcessorId: String, tag: String): Future[Offset] = {
    session.selectOne(
      s"SELECT timeUuidOffset, sequenceOffset FROM offsetStore WHERE eventProcessorId = ? AND tag = ?",
      eventProcessorId, tag
    ).map(extractOffset)
  }

  protected def extractOffset(maybeRow: Option[Row]): Offset = {
    maybeRow match {
      case Some(row) =>
        val uuid = row.getUUID("timeUuidOffset")
        if (uuid != null) {
          new TimeBasedUUID(uuid)
        } else {
          if (row.isNull("sequenceOffset")) {
            NoOffset
          } else {
            new Sequence(row.getLong("sequenceOffset"))
          }
        }
      case None => NoOffset
    }
  }

}

/**
 * Internal API
 */
private[lagom] final class CassandraOffsetDao(session: CassandraSession, statement: PreparedStatement,
                                              eventProcessorId: String, tag: String, override val loadedOffset: Offset) extends OffsetDao {
  override def saveOffset(offset: Offset): Future[Done] = {
    session.executeWrite(bindSaveOffset(offset))
  }
  def bindSaveOffset(offset: Offset): BoundStatement = {
    offset match {
      case NoOffset            => statement.bind(eventProcessorId, tag, null, null)
      case seq: Sequence       => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq.value))
      case uuid: TimeBasedUUID => statement.bind(eventProcessorId, tag, uuid.value, null)
    }
  }
}
