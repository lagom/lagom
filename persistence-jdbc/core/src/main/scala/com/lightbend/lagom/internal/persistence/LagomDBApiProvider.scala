/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import com.typesafe.config.Config
import javax.inject.{ Inject, Singleton }
import play.api.db.{ ConnectionPool, DBApi, DBApiProvider, DefaultDBApi }
import play.api.inject.{ ApplicationLifecycle, Injector, NewInstanceInjector }
import play.api.{ Configuration, Environment, Logger }
import scala.util.Try
import scala.concurrent.Future
import scala.util.control.NonFatal

// This overrides the DBApiProvider from Play with Lagom-specific behavior
// TODO: file an issue in Play to make this configurable so we can remove the override
@Singleton
private[lagom] class LagomDBApiProvider @Inject() (
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

  private val logger = Logger(classOf[LagomDBApiProvider])

  override lazy val get: DBApi = {
    val config = configuration.underlying
    val dbKey = config.getString("play.db.config")
    val pool = ConnectionPool.fromConfig(config.getString("play.db.pool"), injector, environment, defaultConnectionPool)
    val configs = if (config.hasPath(dbKey)) {
      Configuration(config).getPrototypedMap(dbKey, "play.db.prototype").mapValues(_.underlying)
    } else Map.empty[String, Config]
    val db = new DefaultDBApi(configs, pool, environment, injector)
    lifecycle.addStopHook { () => Future.fromTry(Try(db.shutdown())) }
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
