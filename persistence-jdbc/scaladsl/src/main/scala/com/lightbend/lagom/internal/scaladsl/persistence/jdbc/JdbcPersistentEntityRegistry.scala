/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.jdbc

import akka.actor.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.scaladsl.EventsByTagQuery2
import akka.persistence.query.{ NoOffset, Offset, PersistenceQuery, Sequence }
import com.lightbend.lagom.internal.persistence.jdbc.SlickProvider
import com.lightbend.lagom.internal.scaladsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

/**
 * INTERNAL API
 */
private[lagom] final class JdbcPersistentEntityRegistry(system: ActorSystem, slickProvider: SlickProvider)
  extends AbstractPersistentEntityRegistry(system) {

  private lazy val ensureTablesCreated = slickProvider.ensureTablesCreated()

  override def register(entityFactory: => PersistentEntity): Unit = {
    ensureTablesCreated
    super.register(entityFactory)
  }

  override protected val journalId: String = JdbcReadJournal.Identifier
  private val jdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](journalId)
  override protected val eventsByTagQuery: Option[EventsByTagQuery2] = Some(jdbcReadJournal)

  override protected def mapStartingOffset(storedOffset: Offset): Offset = storedOffset match {
    case NoOffset        => NoOffset
    case Sequence(value) => Sequence(value + 1)
    case other =>
      throw new IllegalArgumentException(s"JDBC does not support ${other.getClass.getSimpleName} offsets")
  }

}
