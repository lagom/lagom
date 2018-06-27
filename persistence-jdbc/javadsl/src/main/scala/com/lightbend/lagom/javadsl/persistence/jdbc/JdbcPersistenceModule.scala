/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import akka.actor.ActorSystem
import com.google.inject.{ AbstractModule, Key, Provider }
import com.lightbend.lagom.internal.javadsl.persistence.jdbc._
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

// This overrides the DBApiProvider from Play with Lagom-specific behavior
// TODO: file an issue in Play to make this configurable so we can remove the override
@Singleton
private class LagomDBApiProvider @Inject() (
  environment:           Environment,
  configuration:         Configuration,
  defaultConnectionPool: ConnectionPool,
  lifecycle:             ApplicationLifecycle,
  injector:              Injector             = NewInstanceInjector
) extends DBApiProvider(
  environment,
  configuration,
  defaultConnectionPool,
  lifecycle,
  injector
) {

  override lazy val get: DBApi = {
    val config = configuration.underlying
    val dbKey = config.getString("play.db.config")
    val pool = ConnectionPool.fromConfig(config.getString("play.db.pool"), injector, environment, defaultConnectionPool)
    val configs = if (config.hasPath(dbKey)) {
      Configuration(config).getPrototypedMap(dbKey, "play.db.prototype").mapValues(_.underlying)
    } else Map.empty[String, Config]
    val db = new DefaultDBApi(configs, pool, environment, injector)
    lifecycle.addStopHook { () => Future.successful(db.shutdown()) }
    // The only difference between this and the parent implementation in Play
    // is that Play tries to connect eagerly to the database on startup and
    // fails on misconfiguration:
    //
    // db.connect(logConnection = environment.mode != Mode.Test)
    //
    // In Lagom, we don't want to depend on the database being up when the
    // service starts.
    // Instead, it should recover automatically when the DB comes up.
    db
  }

}
