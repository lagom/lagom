/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.internal.scaladsl.persistence.OffsetStore
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
  lazy val cassandraSession: CassandraSession = new CassandraSession(actorSystem)

  // FIXME CassandraOffsetStore (internal) vs OffsetStore, guice published both
  lazy val cassandraOffsetStore: CassandraOffsetStore = new CassandraOffsetStore(actorSystem, cassandraSession, readSideConfig)(executionContext)
  lazy val offsetStore: OffsetStore = cassandraOffsetStore

  lazy val cassandraReadSide: CassandraReadSide = new CassandraReadSideImpl(actorSystem, cassandraSession, cassandraOffsetStore)
}

