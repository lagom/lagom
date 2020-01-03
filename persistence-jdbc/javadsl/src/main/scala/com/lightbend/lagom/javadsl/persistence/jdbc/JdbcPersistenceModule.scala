/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jdbc

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.javadsl.persistence.jdbc._
import com.lightbend.lagom.internal.persistence.jdbc.SlickDbProvider
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetStore
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.spi.persistence.OffsetStore
import play.api.Configuration
import play.api.Environment
import play.api.db.DBApi
import play.api.inject.ApplicationLifecycle
import play.api.inject.Binding
import play.api.inject.Module

import scala.concurrent.ExecutionContext

class JdbcPersistenceModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[SlickProvider].toProvider[GuiceSlickProvider],
    bind[JdbcReadSide].to[JdbcReadSideImpl],
    bind[PersistentEntityRegistry].to[JdbcPersistentEntityRegistry],
    bind[JdbcSession].to[JdbcSessionImpl],
    bind[SlickOffsetStore].to[JavadslJdbcOffsetStore],
    bind[OffsetStore].to(bind[SlickOffsetStore])
  )
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
