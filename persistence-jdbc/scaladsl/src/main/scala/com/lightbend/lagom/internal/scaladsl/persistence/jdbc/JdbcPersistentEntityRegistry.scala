/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.persistence.jdbc

import akka.actor.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.NoOffset
import akka.persistence.query.Offset
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.Sequence
import com.lightbend.lagom.internal.persistence.jdbc.SlickProvider
import com.lightbend.lagom.internal.scaladsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

/**
 * INTERNAL API
 */
private[lagom] final class JdbcPersistentEntityRegistry(system: ActorSystem, slickProvider: SlickProvider)
    extends AbstractPersistentEntityRegistry(system) {
  private lazy val ensureTablesCreated = slickProvider.ensureTablesCreated()

  override def register(entityFactory: => PersistentEntity): Unit = {
    ensureTablesCreated
    super.register(entityFactory)
  }

  protected override val queryPluginId = Some(JdbcReadJournal.Identifier)
}
