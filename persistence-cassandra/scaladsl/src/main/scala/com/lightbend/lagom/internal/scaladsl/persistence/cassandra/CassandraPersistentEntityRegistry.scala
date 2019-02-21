/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import com.lightbend.lagom.internal.persistence.PersistenceConfig
import com.lightbend.lagom.internal.persistence.cassandra.CassandraKeyspaceConfig
import com.lightbend.lagom.internal.scaladsl.persistence.AbstractPersistentEntityRegistry

/**
 * Internal API
 */
private[lagom] final class CassandraPersistentEntityRegistry(system: ActorSystem, config: PersistenceConfig)
  extends AbstractPersistentEntityRegistry(system, config) {

  private val log = Logging.getLogger(system, getClass)

  CassandraKeyspaceConfig.validateKeyspace("cassandra-journal", system.settings.config, log)
  CassandraKeyspaceConfig.validateKeyspace("cassandra-snapshot-store", system.settings.config, log)

  override protected val queryPluginId = Some(CassandraReadJournal.Identifier)
}
