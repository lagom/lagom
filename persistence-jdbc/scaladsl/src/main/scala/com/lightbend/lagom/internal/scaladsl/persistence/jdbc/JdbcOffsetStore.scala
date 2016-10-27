/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.jdbc

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.{ AbstractSlickOffsetStoreConfiguration, SlickOffsetStore, SlickProvider }
import com.lightbend.lagom.internal.scaladsl.persistence.{ OffsetDao, OffsetStore }
import com.lightbend.lagom.scaladsl.persistence.{ NoOffset, Offset, Sequence, TimeBasedUUID }
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
 * INTERNAL API
 */
class OffsetTableConfiguration(config: Configuration, readSideConfig: ReadSideConfig)
  extends AbstractSlickOffsetStoreConfiguration(config) {
  override def minBackoff: FiniteDuration = readSideConfig.minBackoff
  override def maxBackoff: FiniteDuration = readSideConfig.maxBackoff
  override def randomBackoffFactor: Double = readSideConfig.randomBackoffFactor
  override def globalPrepareTimeout: FiniteDuration = readSideConfig.globalPrepareTimeout
  override def role: Option[String] = readSideConfig.role
  override def toString: String = s"OffsetTableConfiguration($tableName,$schemaName)"
}

/**
 * INTERNAL API
 */
private[lagom] class JdbcOffsetStore(val slick: SlickProvider, system: ActorSystem, tableConfig: OffsetTableConfiguration,
                                     readSideConfig: ReadSideConfig)(implicit ec: ExecutionContext)
  extends SlickOffsetStore(system, slick, tableConfig)
  with OffsetStore {

  type DslOffset = Offset

  override protected def queryToOffsetRow(id: String, tag: String, offset: Offset): OffsetRow =
    offset match {
      case Sequence(value)     => OffsetRow(id, tag, Some(value), None)
      case TimeBasedUUID(uuid) => OffsetRow(id, tag, None, Some(uuid.toString))
      case NoOffset            => OffsetRow(id, tag, None, None)
    }

  override protected def offsetRowToOffset(row: Option[OffsetRow]): Offset = {
    row.flatMap(row => row.sequenceOffset.map(Sequence).orElse(
      row.timeUuidOffset.flatMap(uuid => Try(UUID.fromString(uuid)).toOption)
        .filter(_.version == 1)
        .map(TimeBasedUUID)
    )).getOrElse(NoOffset)
  }

  override def prepare(eventProcessorId: String, tag: String): Future[JdbcOffsetDao] = {
    runPreparations(eventProcessorId, tag)
      .map(offset => new JdbcOffsetDao(this, eventProcessorId, tag, offset))
  }
}

class JdbcOffsetDao(jdbcOffsetStore: JdbcOffsetStore, readSideId: String, tag: String,
                    override val loadedOffset: Offset)(implicit ec: ExecutionContext) extends OffsetDao {

  override def saveOffset(offset: Offset): Future[Done] = {
    jdbcOffsetStore.slick.db.run(jdbcOffsetStore.updateOffsetQuery(readSideId, tag, offset)
      .map(_ => Done.getInstance()))
  }

  def updateOffsetQuery(offset: Offset) = {
    jdbcOffsetStore.updateOffsetQuery(readSideId, tag, offset)
  }
}
