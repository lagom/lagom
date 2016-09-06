/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.persistence.Persistence
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import com.google.inject.Injector
import com.lightbend.lagom.internal.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.PersistentEntity

@Singleton
private[lagom] class JdbcPersistentEntityRegistry @Inject() (system: ActorSystem, injector: Injector, slickProvider: SlickProvider)
  extends AbstractPersistentEntityRegistry(system, injector) {

  private lazy val ensureTablesCreated = slickProvider.ensureTablesCreated()

  override def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit = {
    ensureTablesCreated
    super.register(entityClass)
  }

  override protected val journalId: String = JdbcReadJournal.Identifier
  override protected val eventQueries: JdbcReadJournal =
    PersistenceQuery(system).readJournalFor[JdbcReadJournal](journalId)
}
