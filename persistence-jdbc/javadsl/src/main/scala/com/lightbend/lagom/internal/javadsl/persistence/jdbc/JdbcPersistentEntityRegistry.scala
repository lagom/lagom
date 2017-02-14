/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.scaladsl.EventsByTagQuery2
import akka.persistence.query.{ NoOffset, PersistenceQuery, Sequence, Offset => AkkaOffset }
import com.google.inject.Injector
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.{ Offset, PersistentEntity }

/**
 * INTERNAL API
 */
@Singleton
private[lagom] final class JdbcPersistentEntityRegistry @Inject() (system: ActorSystem, injector: Injector, slickProvider: SlickProvider)
  extends AbstractPersistentEntityRegistry(system, injector) {

  private lazy val ensureTablesCreated = slickProvider.ensureTablesCreated()

  override def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit = {
    ensureTablesCreated
    super.register(entityClass)
  }

  override protected val journalId: String = JdbcReadJournal.Identifier
  private val jdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](journalId)
  override protected val eventsByTagQuery: Option[EventsByTagQuery2] = Some(jdbcReadJournal)

  override protected def mapStartingOffset(storedOffset: Offset): AkkaOffset = storedOffset match {
    case Offset.NONE          => NoOffset
    case seq: Offset.Sequence => Sequence(seq.value() + 1)
    case other =>
      throw new IllegalArgumentException(s"JDBC does not support ${other.getClass.getSimpleName} offsets")
  }

}
