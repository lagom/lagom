/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.jdbc.{ SlickOffsetStore, SlickProvider }
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

/**
 * Persistence JDBC components (for compile-time injection).
 */
trait JdbcPersistenceComponents extends PersistenceComponents with ReadSideJdbcPersistenceComponents

/**
 * Write-side persistence JDBC components (for compile-time injection).
 */
trait WriteSideJdbcPersistenceComponents extends WriteSidePersistenceComponents with DBComponents {

  def actorSystem: ActorSystem
  def executionContext: ExecutionContext

  lazy val slickProvider: SlickProvider = new SlickProvider(actorSystem, dbApi)(executionContext)
  override lazy val persistentEntityRegistry: PersistentEntityRegistry =
    new JdbcPersistentEntityRegistry(actorSystem, slickProvider)

}

/**
 * Read-side persistence JDBC components (for compile-time injection).
 */
trait ReadSideJdbcPersistenceComponents extends ReadSidePersistenceComponents with WriteSideJdbcPersistenceComponents {

  lazy val offsetTableConfiguration: OffsetTableConfiguration = new OffsetTableConfiguration(
    configuration, readSideConfig
  )
  private[lagom] lazy val slickOffsetStore: SlickOffsetStore = new SlickOffsetStore(actorSystem, slickProvider,
    offsetTableConfiguration)

  lazy val offsetStore: OffsetStore = slickOffsetStore

  lazy val jdbcReadSide: JdbcReadSide = new JdbcReadSideImpl(slickProvider, slickOffsetStore)(executionContext)

  lazy val jdbcSession: JdbcSession = new JdbcSessionImpl(slickProvider)

}
