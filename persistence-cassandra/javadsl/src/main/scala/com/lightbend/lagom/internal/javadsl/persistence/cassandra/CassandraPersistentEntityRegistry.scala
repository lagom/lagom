/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.cassandra

import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery
import com.google.inject.Injector
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.internal.persistence.cassandra.CassandraKeyspaceConfig

/**
 * Internal API
 */
@Singleton
private[lagom] final class CassandraPersistentEntityRegistry @Inject() (system: ActorSystem, injector: Injector)
  extends AbstractPersistentEntityRegistry(system, injector) {

  private val log = Logging.getLogger(system, getClass)

  CassandraKeyspaceConfig.validateKeyspace("cassandra-journal", system.settings.config, log)
  CassandraKeyspaceConfig.validateKeyspace("cassandra-snapshot-store", system.settings.config, log)

  override protected val journalId = CassandraReadJournal.Identifier

  private val cassandraReadJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](journalId)

  override protected val eventsByTagQuery: Option[EventsByTagQuery] = Some(cassandraReadJournal)

}
