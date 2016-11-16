/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.OffsetStore
import com.lightbend.lagom.internal.persistence.jdbc.SlickProvider
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcOffsetStore
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcReadSideImpl
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcSessionImpl
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.OffsetTableConfiguration
import com.lightbend.lagom.scaladsl.persistence.PersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.ReadSidePersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.WriteSidePersistenceComponents
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
  lazy val jdbcOffsetStore: JdbcOffsetStore = new JdbcOffsetStore(slickProvider, actorSystem, offsetTableConfiguration,
    readSideConfig)(executionContext)

  // FIXME what about OffsetStore? OffsetStore is internal. Guice module defines:
  lazy val offsetStore: OffsetStore = jdbcOffsetStore

  lazy val jdbcReadSide: JdbcReadSide = new JdbcReadSideImpl(slickProvider, jdbcOffsetStore)(executionContext)

  lazy val jdbcSession: JdbcSession = new JdbcSessionImpl(slickProvider)

}
