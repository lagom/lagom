/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

import akka.actor.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.NoOffset
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.Sequence
import akka.persistence.query.{ Offset => AkkaOffset }
import com.google.inject.Injector
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.javadsl.persistence.PersistentEntity

/**
 * INTERNAL API
 */
@Singleton
private[lagom] final class JdbcPersistentEntityRegistry @Inject()(
    system: ActorSystem,
    injector: Injector,
    slickProvider: SlickProvider
) extends AbstractPersistentEntityRegistry(system, injector) {

  private lazy val ensureTablesCreated = slickProvider.ensureTablesCreated()

  override def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit = {
    ensureTablesCreated
    super.register(entityClass)
  }

  protected override val queryPluginId = Optional.of(JdbcReadJournal.Identifier)

}
