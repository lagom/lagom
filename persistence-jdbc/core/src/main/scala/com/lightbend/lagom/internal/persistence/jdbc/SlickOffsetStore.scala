/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.query.{ NoOffset, Offset, Sequence, TimeBasedUUID }
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import play.api.Configuration

import scala.concurrent.Future
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
private[lagom] abstract class SlickOffsetStore(system: ActorSystem, slick: SlickProvider, config: SlickOffsetStoreConfiguration) {

  case class OffsetRow(id: String, tag: String, sequenceOffset: Option[Long], timeUuidOffset: Option[String])

  protected type DslOffset
  protected def offsetToDslOffset(offset: Offset): DslOffset
  protected def dslOffsetToOffset(dslOffset: DslOffset): Offset

  private def queryToOffsetRow(id: String, tag: String, offset: DslOffset): OffsetRow =
    dslOffsetToOffset(offset) match {
      case Sequence(value)     => OffsetRow(id, tag, Some(value), None)
      case TimeBasedUUID(uuid) => OffsetRow(id, tag, None, Some(uuid.toString))
      case NoOffset            => OffsetRow(id, tag, None, None)
    }

  private def offsetRowToOffset(row: Option[OffsetRow]): DslOffset = {
    val offset = row.flatMap(row => row.sequenceOffset.map(Sequence).orElse(
      row.timeUuidOffset.flatMap(uuid => Try(UUID.fromString(uuid)).toOption)
        .filter(_.version == 1)
        .map(TimeBasedUUID)
    )).getOrElse(NoOffset)
    offsetToDslOffset(offset)
  }

  import system.dispatcher
  import slick.profile.api._

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

  def runPreparations(eventProcessorId: String, tag: String): Future[DslOffset] = {
    implicit val timeout = Timeout(config.globalPrepareTimeout)
    for {
      _ <- startupTask.askExecute()
      offset <- slick.db.run(getOffsetQuery(eventProcessorId, tag))
    } yield offset
  }

  def updateOffsetQuery(id: String, tag: String, offset: DslOffset) = {
    offsets.insertOrUpdate(queryToOffsetRow(id, tag, offset))
  }

  private def getOffsetQuery(id: String, tag: String): DBIOAction[DslOffset, NoStream, Effect.Read] = {
    (for {
      offset <- offsets if offset.id === id && offset.tag === tag
    } yield {
      offset
    }).result.headOption.map(offsetRowToOffset)
  }

  private def createTables() = {
    // The schema will be wrong due to our work around for https://github.com/slick/slick/issues/966 above, so need to
    // remove the primary key declarations from those columns
    val statements = offsets.schema.createStatements.map(_.replace(" PRIMARY KEY,", ",")).toSeq
    slick.db.run(slick.createTable(statements, slick.tableExists(config.schemaName, config.tableName))
      .map(_ => Done.getInstance()))
  }

}