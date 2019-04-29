/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import java.util.Optional

import javax.inject.{ Inject, Singleton }
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.internal.persistence.PersistenceConfig
import com.lightbend.lagom.javadsl.persistence.PersistentEntity
import play.api.inject.Injector

/**
 * INTERNAL API
 */
@Singleton
private[lagom] final class JdbcPersistentEntityRegistry @Inject() (system: ActorSystem, injector: Injector, slickProvider: SlickProvider, config: PersistenceConfig)
  extends AbstractPersistentEntityRegistry(system, injector, config) {

  private lazy val ensureTablesCreated = slickProvider.ensureTablesCreated()

  override def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit = {
    if (config.delayRegistration) {
      Cluster(system).registerOnMemberUp {
        ensureTablesCreated
      }
    } else ensureTablesCreated
    super.register(entityClass)
  }

  override protected val queryPluginId = Optional.of(JdbcReadJournal.Identifier)

}
