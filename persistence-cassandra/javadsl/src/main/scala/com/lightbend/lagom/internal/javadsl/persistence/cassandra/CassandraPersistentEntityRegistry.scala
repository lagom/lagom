/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.cassandra

import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery2
import com.google.inject.Injector
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry

/**
 * Internal API
 */
@Singleton
private[lagom] final class CassandraPersistentEntityRegistry @Inject() (system: ActorSystem, injector: Injector)
  extends AbstractPersistentEntityRegistry(system, injector) {

  override protected val journalId = CassandraReadJournal.Identifier

  private val cassandraReadJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](journalId)

  override protected val eventsByTagQuery: Option[EventsByTagQuery2] = Some(cassandraReadJournal)

}
