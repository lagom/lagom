/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.query.{ NoOffset, Offset, Sequence => AkkaSequence, TimeBasedUUID }
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.spi.persistence.{ OffsetDao, OffsetStore }
import play.api.Configuration

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
 * INTERNAL API
 */
private[lagom] trait SlickOffsetStoreConfiguration {
  def tableName: String
  def schemaName: Option[String]
  def idColumnName: String
  def tagColumnName: String
  def sequenceOffsetColumnName: String
  def timeUuidOffsetColumnName: String

  def minBackoff: FiniteDuration
  def maxBackoff: FiniteDuration
  def randomBackoffFactor: Double
  def globalPrepareTimeout: FiniteDuration
  def role: Option[String]
}

/**
 * INTERNAL API
 */
private[lagom] abstract class AbstractSlickOffsetStoreConfiguration(config: Configuration) extends SlickOffsetStoreConfiguration {
  private val cfg = config.underlying.getConfig("lagom.persistence.read-side.jdbc.tables.offset")
  val tableName: String = cfg.getString("tableName")
  val schemaName: Option[String] = Option(cfg.getString("schemaName")).filter(_.trim != "")
  private val columnsCfg = cfg.getConfig("columnNames")
  val idColumnName: String = columnsCfg.getString("readSideId")
  val tagColumnName: String = columnsCfg.getString("tag")
  val sequenceOffsetColumnName: String = columnsCfg.getString("sequenceOffset")
  val timeUuidOffsetColumnName: String = columnsCfg.getString("timeUuidOffset")
  override def toString: String = s"OffsetTableConfiguration($tableName,$schemaName)"
}

/**
 * INTERNAL API
 */
private[lagom] class SlickOffsetStore(system: ActorSystem, val slick: SlickProvider, config: SlickOffsetStoreConfiguration) extends OffsetStore {

  case class OffsetRow(id: String, tag: String, sequenceOffset: Option[Long], timeUuidOffset: Option[String])

  import system.dispatcher
  import slick.profile.api._

  override def prepare(eventProcessorId: String, tag: String): Future[SlickOffsetDao] = {
    runPreparations(eventProcessorId, tag).map(offset =>
      new SlickOffsetDao(this, eventProcessorId, tag, offset))
  }

  private class OffsetStore(_tag: Tag) extends Table[OffsetRow](_tag, _schemaName = config.schemaName, _tableName = config.tableName) {
    def * = (id, tag, sequenceOffset, timeUuidOffset) <> (OffsetRow.tupled, OffsetRow.unapply)

    // Technically these two columns shouldn't have the primary key options, but they need it to work around
    // https://github.com/slick/slick/issues/966
    val id = column[String](config.idColumnName, O.Length(255, varying = true), O.PrimaryKey)
    val tag = column[String](config.tagColumnName, O.Length(255, varying = true), O.PrimaryKey)
    val sequenceOffset = column[Option[Long]](config.sequenceOffsetColumnName)
    val timeUuidOffset = column[Option[String]](config.timeUuidOffsetColumnName, O.Length(36, varying = false))
    val pk = primaryKey(s"${config.tableName}_pk", (id, tag))
  }

  private val offsets = TableQuery[OffsetStore]

  val startupTask = ClusterStartupTask(
    system,
    "cassandraOffsetStorePrepare",
    createTables,
    config.globalPrepareTimeout,
    config.role,
    config.minBackoff,
    config.maxBackoff,
    config.randomBackoffFactor
  )

  def runPreparations(eventProcessorId: String, tag: String): Future[Offset] = {
    implicit val timeout = Timeout(config.globalPrepareTimeout)
    for {
      _ <- startupTask.askExecute()
      offset <- slick.db.run(getOffsetQuery(eventProcessorId, tag))
    } yield offset
  }

  def updateOffsetQuery(id: String, tag: String, offset: Offset) = {
    offsets.insertOrUpdate(queryToOffsetRow(id, tag, offset))
  }

  private def queryToOffsetRow(id: String, tag: String, offset: Offset): OffsetRow = {
    offset match {
      case AkkaSequence(value)  => OffsetRow(id, tag, Some(value), None)
      case TimeBasedUUID(value) => OffsetRow(id, tag, None, Some(value.toString))
      case NoOffset             => OffsetRow(id, tag, None, None)
    }
  }

  private def getOffsetQuery(id: String, tag: String): DBIOAction[Offset, NoStream, Effect.Read] = {
    (for {
      offset <- offsets if offset.id === id && offset.tag === tag
    } yield {
      offset
    }).result.headOption.map(offsetRowToOffset)
  }

  private def offsetRowToOffset(row: Option[OffsetRow]): Offset = {
    row.flatMap(row => row.sequenceOffset.map(AkkaSequence).orElse(
      row.timeUuidOffset.flatMap(uuid => Try(UUID.fromString(uuid)).toOption)
        .filter(_.version == 1)
        .map(TimeBasedUUID)
    )).getOrElse(NoOffset)
  }

  private def createTables() = {
    // The schema will be wrong due to our work around for https://github.com/slick/slick/issues/966 above, so need to
    // remove the primary key declarations from those columns
    val statements = offsets.schema.createStatements.map(_.replace(" PRIMARY KEY,", ",")).toSeq
    slick.db.run(slick.createTable(statements, slick.tableExists(config.schemaName, config.tableName))
      .map(_ => Done.getInstance()))
  }

}

private[lagom] class SlickOffsetDao(slickOffsetStore: SlickOffsetStore, readSideId: String, tag: String,
                                    override val loadedOffset: Offset)(implicit ec: ExecutionContext) extends OffsetDao {

  override def saveOffset(offset: Offset): Future[Done] = {
    slickOffsetStore.slick.db.run(slickOffsetStore.updateOffsetQuery(readSideId, tag, offset)
      .map(_ => Done.getInstance()))
  }

  def updateOffsetQuery(offset: Offset) = {
    slickOffsetStore.updateOffsetQuery(readSideId, tag, offset)
  }
}
