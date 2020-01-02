/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence.cassandra

import java.util.Optional

import javax.inject.Inject
import javax.inject.Singleton
import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.internal.persistence.cassandra.CassandraKeyspaceConfig
import play.api.inject.Injector

/**
 * Internal API
 */
@Singleton
private[lagom] final class CassandraPersistentEntityRegistry @Inject() (system: ActorSystem, injector: Injector)
    extends AbstractPersistentEntityRegistry(system, injector) {
  private val log = Logging.getLogger(system, getClass)

  CassandraKeyspaceConfig.validateKeyspace("cassandra-journal", system.settings.config, log)
  CassandraKeyspaceConfig.validateKeyspace("cassandra-snapshot-store", system.settings.config, log)

  protected override val queryPluginId = Optional.of(CassandraReadJournal.Identifier)
}
