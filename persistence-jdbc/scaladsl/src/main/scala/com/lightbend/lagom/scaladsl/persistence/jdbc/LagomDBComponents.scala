/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import com.lightbend.lagom.internal.persistence.LagomDBApiProvider
import play.api.{ Configuration, Environment }
import play.api.db.{ ConnectionPool, DBApi, DBApiProvider, DBComponents }
import play.api.inject.ApplicationLifecycle

/**
 * DB components (for compile-time injection).
 */
trait LagomDBComponents extends DBComponents {
  def environment: Environment
  def configuration: Configuration
  def connectionPool: ConnectionPool
  def applicationLifecycle: ApplicationLifecycle

  override lazy val dbApi: DBApi =
    new LagomDBApiProvider(
      environment,
      configuration,
      connectionPool,
      applicationLifecycle
    ).get
}
