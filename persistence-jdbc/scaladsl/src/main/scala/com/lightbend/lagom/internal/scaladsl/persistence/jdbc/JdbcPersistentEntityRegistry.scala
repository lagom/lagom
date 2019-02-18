/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.persistence.jdbc

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
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
    Cluster(system).registerOnMemberUp {
      ensureTablesCreated
    }
    super.register(entityFactory)
  }

  override protected val queryPluginId = Some(JdbcReadJournal.Identifier)

}
