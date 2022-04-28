/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.jdbc

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import com.lightbend.lagom.internal.persistence.jdbc.SlickDbProvider
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetStore
import com.lightbend.lagom.internal.persistence.jdbc.SlickProvider
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcReadSideImpl
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcSessionImpl
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.OffsetTableConfiguration
import com.lightbend.lagom.scaladsl.persistence.PersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.ReadSidePersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.WriteSidePersistenceComponents
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

trait SlickProviderComponents extends DBComponents {
  def actorSystem: ActorSystem
  def coordinatedShutdown: CoordinatedShutdown
  def executionContext: ExecutionContext

  lazy val slickProvider: SlickProvider = {
    // Ensures JNDI bindings are made before we build the SlickProvider
    SlickDbProvider.buildAndBindSlickDatabases(
      dbApi,
      actorSystem.settings.config,
      coordinatedShutdown
    )(executionContext)
    new SlickProvider(actorSystem, coordinatedShutdown)(executionContext)
  }


  // Eagerly initialize the SlickProvider. Allows for explicily initialization of underlying datasource for Slick.
  // (see https://github.com/lagom/lagom/issues/3349)
  slickProvider.getClass

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
    configuration.underlying,
    readSideConfig
  )
  private[lagom] lazy val slickOffsetStore: SlickOffsetStore =
    new SlickOffsetStore(actorSystem, slickProvider, offsetTableConfiguration)

  lazy val offsetStore: OffsetStore = slickOffsetStore

  lazy val jdbcReadSide: JdbcReadSide = new JdbcReadSideImpl(slickProvider, slickOffsetStore)(executionContext)

  lazy val jdbcSession: JdbcSession = new JdbcSessionImpl(slickProvider)
}
