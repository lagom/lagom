/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.cassandra

import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import akka.persistence.query.Offset
import akka.util.Timeout
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.persistence.cassandra.AbstractCassandraOffsetStore
import com.lightbend.lagom.internal.persistence.{ OffsetStore, ReadSideConfig }
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import com.lightbend.lagom.javadsl.persistence.{ Offset => LagomJavadslOffset }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Internal API
 */
@Singleton
private[lagom] final class CassandraOffsetStore @Inject() (system: ActorSystem, session: CassandraSession, config: ReadSideConfig)(implicit ec: ExecutionContext)
  extends AbstractCassandraOffsetStore(system, session.scalaDelegate, config) with OffsetStore {

  override type DslOffset = com.lightbend.lagom.javadsl.persistence.Offset
  override protected def offsetToDslOffset(offset: Offset): LagomJavadslOffset =
    OffsetAdapter.offsetToDslOffset(offset)
  override protected def dslOffsetToOffset(dslOffset: LagomJavadslOffset): Offset =
    OffsetAdapter.dslOffsetToOffset(dslOffset)

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
      case (offset, statement) => new CassandraOffsetDao(statement, eventProcessorId, tag, dslOffsetToOffset(offset))
    }
  }
}
