/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import akka.persistence.cassandra.session.CassandraSessionSettings
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ CassandraOffsetStore, CassandraPersistentEntityRegistry, CassandraReadSideImpl }
import com.lightbend.lagom.scaladsl.persistence.{ PersistenceComponents, PersistentEntityRegistry, ReadSidePersistenceComponents, WriteSidePersistenceComponents }

/**
 * Persistence Cassandra components (for compile-time injection).
 */
trait CassandraPersistenceComponents extends PersistenceComponents
  with ReadSideCassandraPersistenceComponents
  with WriteSideCassandraPersistenceComponents

/**
 * Write-side persistence Cassandra components (for compile-time injection).
 */
trait WriteSideCassandraPersistenceComponents extends WriteSidePersistenceComponents {
  override lazy val persistentEntityRegistry: PersistentEntityRegistry =
    new CassandraPersistentEntityRegistry(actorSystem)

}

/**
 * Read-side persistence Cassandra components (for compile-time injection).
 */
trait ReadSideCassandraPersistenceComponents extends ReadSidePersistenceComponents {
  implicit private val ec = actorSystem.dispatcher
  lazy val offsetStore: CassandraOffsetStore = new CassandraOffsetStore(actorSystem, cassandraSession, readSideConfig)
  lazy val cassandraSessionSettings: CassandraSessionSettings =
    CassandraSessionSettings(actorSystem.settings.config.getConfig("cassandra-journal"))
  lazy val cassandraSession: CassandraSession = new CassandraSession(actorSystem, cassandraSessionSettings, executionContext)
  lazy val cassandraReadSide: CassandraReadSide = new CassandraReadSideImpl(actorSystem, cassandraSession, offsetStore)

}

