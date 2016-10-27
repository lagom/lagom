/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import com.datastax.driver.core.{ BoundStatement, PreparedStatement, Row }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask

import scala.concurrent.Future

/**
 * Internal API
 */
private[lagom] abstract class AbstractCassandraOffsetStore(
  system:  ActorSystem,
  session: akka.persistence.cassandra.session.scaladsl.CassandraSession,
  config:  ReadSideConfig
) {

  type DslOffset
  import system.dispatcher

  protected def extractOffset(maybeRow: Option[Row]): DslOffset
  protected def writeOffset(statement: PreparedStatement, eventProcessorId: String, tag: String, offset: DslOffset): BoundStatement

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

}
