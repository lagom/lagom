/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import akka.actor.ActorSystem
import com.google.inject.{ AbstractModule, Key, Provider }
import com.lightbend.lagom.internal.javadsl.persistence.jdbc._
import com.lightbend.lagom.internal.persistence.LagomDBApiProvider
import com.lightbend.lagom.internal.persistence.jdbc.{ SlickDbProvider, SlickOffsetStore }
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.spi.persistence.OffsetStore
import com.typesafe.config.Config
import javax.inject.{ Inject, Singleton }
import play.api.db.{ ConnectionPool, DBApi, DBApiProvider, DefaultDBApi }
import play.api.inject.{ ApplicationLifecycle, Injector, NewInstanceInjector }
import play.api.{ Configuration, Environment }

import scala.concurrent.{ ExecutionContext, Future }

class JdbcPersistenceModule extends AbstractModule {
  override def configure(): Unit = {

    bind(classOf[SlickProvider]).toProvider(classOf[GuiceSlickProvider])
    bind(classOf[JdbcReadSide]).to(classOf[JdbcReadSideImpl])
    bind(classOf[PersistentEntityRegistry]).to(classOf[JdbcPersistentEntityRegistry])
    bind(classOf[JdbcSession]).to(classOf[JdbcSessionImpl])
    bind(classOf[SlickOffsetStore]).to(classOf[JavadslJdbcOffsetStore])
    bind(classOf[OffsetStore]).to(Key.get(classOf[SlickOffsetStore]))
    // Play has no explicit binding for DBApiProvider, it uses a just-in-time
    // binding https://github.com/google/guice/wiki/JustInTimeBindings
    // We rely on that to substitute our own implementation instead:
    bind(classOf[DBApiProvider]).to(classOf[LagomDBApiProvider])
  }
}

@Singleton
class GuiceSlickProvider @Inject() (dbApi: DBApi, actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext)
  extends Provider[SlickProvider] {

  lazy val get = {
    // Ensures JNDI bindings are made before we build the SlickProvider
    SlickDbProvider.buildAndBindSlickDatabases(dbApi, actorSystem.settings.config, applicationLifecycle)
    new SlickProvider(actorSystem)
  }
}

