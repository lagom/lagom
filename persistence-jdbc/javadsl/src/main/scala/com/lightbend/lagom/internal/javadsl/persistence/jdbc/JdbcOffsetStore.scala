/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import javax.inject.{ Inject, Singleton }

import akka.Done
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.persistence.jdbc.{ AbstractSlickOffsetStoreConfiguration, SlickOffsetStore }
import com.lightbend.lagom.internal.persistence.{ OffsetDao, OffsetStore, ReadSideConfig }
import com.lightbend.lagom.javadsl.persistence.Offset
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

/**
 * INTERNAL API
 */
@Singleton
private[lagom] class OffsetTableConfiguration @Inject() (config: Configuration, readSideConfig: ReadSideConfig)
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
@Singleton
private[lagom] class JdbcOffsetStore @Inject() (val slick: SlickProvider, system: ActorSystem, tableConfig: OffsetTableConfiguration,
                                                readSideConfig: ReadSideConfig)(implicit ec: ExecutionContext)
  extends SlickOffsetStore(system, slick, tableConfig)
  with OffsetStore {

  type DslOffset = Offset

  override protected def offsetToDslOffset(offset: akka.persistence.query.Offset): DslOffset =
    OffsetAdapter.offsetToDslOffset(offset)

  override protected def dslOffsetToOffset(dslOffset: DslOffset): akka.persistence.query.Offset =
    OffsetAdapter.dslOffsetToOffset(dslOffset)

  override def prepare(eventProcessorId: String, tag: String): Future[JdbcOffsetDao] = {
    runPreparations(eventProcessorId, tag).map(offset =>
      new JdbcOffsetDao(this, eventProcessorId, tag, dslOffsetToOffset(offset)))
  }

}

/**
 * INTERNAL API
 */
private[lagom] class JdbcOffsetDao(jdbcOffsetStore: JdbcOffsetStore, readSideId: String, tag: String,
                                   override val loadedOffset: akka.persistence.query.Offset)(implicit ec: ExecutionContext) extends OffsetDao {

  override def saveOffset(offset: akka.persistence.query.Offset): Future[Done] = {
    val javadslOffset = OffsetAdapter.offsetToDslOffset(offset)
    jdbcOffsetStore.slick.db.run(jdbcOffsetStore.updateOffsetQuery(readSideId, tag, javadslOffset)
      .map(_ => Done.getInstance()))
  }

  def updateOffsetQuery(offset: Offset) = {
    jdbcOffsetStore.updateOffsetQuery(readSideId, tag, offset)
  }
}
