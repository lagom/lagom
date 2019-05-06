/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import javax.inject.Inject
import javax.inject.Singleton

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.Key
import com.google.inject.Provider
import com.lightbend.lagom.internal.javadsl.persistence.jdbc._
import com.lightbend.lagom.internal.persistence.jdbc.SlickDbProvider
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetStore
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.spi.persistence.OffsetStore
import play.api.db.DBApi
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

class JdbcPersistenceModule extends AbstractModule {
  override def configure(): Unit = {

    bind(classOf[SlickProvider]).toProvider(classOf[GuiceSlickProvider])
    bind(classOf[JdbcReadSide]).to(classOf[JdbcReadSideImpl])
    bind(classOf[PersistentEntityRegistry]).to(classOf[JdbcPersistentEntityRegistry])
    bind(classOf[JdbcSession]).to(classOf[JdbcSessionImpl])
    bind(classOf[SlickOffsetStore]).to(classOf[JavadslJdbcOffsetStore])
    bind(classOf[OffsetStore]).to(Key.get(classOf[SlickOffsetStore]))
  }
}

@Singleton
class GuiceSlickProvider @Inject()(dbApi: DBApi, actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle)(
    implicit ec: ExecutionContext
) extends Provider[SlickProvider] {

  lazy val get = {
    // Ensures JNDI bindings are made before we build the SlickProvider
    SlickDbProvider.buildAndBindSlickDatabases(dbApi, actorSystem.settings.config, applicationLifecycle)
    new SlickProvider(actorSystem)
  }
}
