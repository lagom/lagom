/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import scala.concurrent.Future
import java.net.URI

import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ CassandraPersistentEntityRegistry, CassandraReadSideImpl, ScaladslCassandraOffsetStore }
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.persistence.{ PersistenceComponents, PersistentEntityRegistry, ReadSidePersistenceComponents, WriteSidePersistenceComponents }
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraReadSideSettings, CassandraOffsetStore, ServiceLocatorAdapter, ServiceLocatorHolder }
import com.lightbend.lagom.spi.persistence.OffsetStore
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

  def serviceLocator: ServiceLocator

  // eager initialization
  private[lagom] val serviceLocatorHolder: ServiceLocatorHolder = {
    val holder = ServiceLocatorHolder(actorSystem)
    holder.setServiceLocator(new ServiceLocatorAdapter {
      override def locateAll(name: String): Future[List[URI]] =
        serviceLocator.locateAll(name)
    })
    holder
  }

}

/**
 * Read-side persistence Cassandra components (for compile-time injection).
 */
trait ReadSideCassandraPersistenceComponents extends ReadSidePersistenceComponents {
  lazy val cassandraSession: CassandraSession = new CassandraSession(actorSystem)
  lazy val testCasReadSideSettings: CassandraReadSideSettings = new CassandraReadSideSettings(actorSystem)

  private[lagom] lazy val cassandraOffsetStore: CassandraOffsetStore =
    new ScaladslCassandraOffsetStore(actorSystem, cassandraSession, testCasReadSideSettings, readSideConfig)(executionContext)
  lazy val offsetStore: OffsetStore = cassandraOffsetStore

  lazy val cassandraReadSide: CassandraReadSide = new CassandraReadSideImpl(actorSystem, cassandraSession, cassandraOffsetStore)
}
