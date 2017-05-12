/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraConsistencyValidator, CassandraOffsetStore, ServiceLocatorAdapter, ServiceLocatorHolder }

import scala.concurrent.Future
import java.net.URI

import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ CassandraPersistentEntityRegistry, CassandraReadSideImpl, ScaladslCassandraOffsetStore }
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.persistence.{ PersistenceComponents, PersistentEntityRegistry, ReadSidePersistenceComponents, WriteSidePersistenceComponents }
import com.lightbend.lagom.spi.persistence.OffsetStore
import play.api.{ Configuration, Environment }

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

  def configuration: Configuration
  def environment: Environment
  require(CassandraConsistencyValidator.validate(configuration, environment))

  // eager initialization
  private[lagom] val serviceLocatorHolder: ServiceLocatorHolder = {
    val holder = ServiceLocatorHolder(actorSystem)
    holder.setServiceLocator(new ServiceLocatorAdapter {
      override def locate(name: String): Future[Option[URI]] = serviceLocator.locate(name)
    })
    holder
  }

}

/**
 * Read-side persistence Cassandra components (for compile-time injection).
 */
trait ReadSideCassandraPersistenceComponents extends ReadSidePersistenceComponents {

  def configuration: Configuration
  def environment: Environment
  require(CassandraConsistencyValidator.validate(configuration, environment))

  lazy val cassandraSession: CassandraSession = new CassandraSession(actorSystem)

  private[lagom] lazy val cassandraOffsetStore: CassandraOffsetStore =
    new ScaladslCassandraOffsetStore(actorSystem, cassandraSession, readSideConfig)(executionContext)
  lazy val offsetStore: OffsetStore = cassandraOffsetStore

  lazy val cassandraReadSide: CassandraReadSide = new CassandraReadSideImpl(actorSystem, cassandraSession, cassandraOffsetStore)
}
