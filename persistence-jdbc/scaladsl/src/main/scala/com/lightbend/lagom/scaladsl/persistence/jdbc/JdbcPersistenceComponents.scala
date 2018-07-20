/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.jdbc

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.jdbc.{ SlickDbProvider, SlickOffsetStore, SlickProvider }
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.{ JdbcPersistentEntityRegistry, JdbcReadSideImpl, JdbcSessionImpl, OffsetTableConfiguration }
import com.lightbend.lagom.scaladsl.persistence.{ PersistenceComponents, PersistentEntityRegistry, ReadSidePersistenceComponents, WriteSidePersistenceComponents }
import com.lightbend.lagom.spi.persistence.OffsetStore
import play.api.db.DBComponents

import scala.concurrent.ExecutionContext

/**
 * Persistence JDBC components (for compile-time injection).
 */
trait JdbcPersistenceComponents
  extends PersistenceComponents
  with ReadSideJdbcPersistenceComponents
  with WriteSideJdbcPersistenceComponents

private[lagom] trait SlickProviderComponents extends DBComponents {

  def actorSystem: ActorSystem
  def executionContext: ExecutionContext

  lazy val slickProvider: SlickProvider = {
    // Ensures JNDI bindings are made before we build the SlickProvider
    SlickDbProvider.buildAndBindSlickDatabases(dbApi, actorSystem.settings.config, applicationLifecycle)
    new SlickProvider(actorSystem)(executionContext)
  }
}

/**
 * Write-side persistence JDBC components (for compile-time injection).
 */
trait WriteSideJdbcPersistenceComponents extends WriteSidePersistenceComponents with SlickProviderComponents {

  def actorSystem: ActorSystem
  def executionContext: ExecutionContext

  override lazy val persistentEntityRegistry: PersistentEntityRegistry =
    new JdbcPersistentEntityRegistry(actorSystem, slickProvider)

}

/**
 * Read-side persistence JDBC components (for compile-time injection).
 */
trait ReadSideJdbcPersistenceComponents extends ReadSidePersistenceComponents with SlickProviderComponents {

  lazy val offsetTableConfiguration: OffsetTableConfiguration = new OffsetTableConfiguration(
    configuration.underlying, readSideConfig
  )
  private[lagom] lazy val slickOffsetStore: SlickOffsetStore = new SlickOffsetStore(actorSystem, slickProvider,
    offsetTableConfiguration)

  lazy val offsetStore: OffsetStore = slickOffsetStore

  lazy val jdbcReadSide: JdbcReadSide = new JdbcReadSideImpl(slickProvider, slickOffsetStore)(executionContext)

  lazy val jdbcSession: JdbcSession = new JdbcSessionImpl(slickProvider)

}
