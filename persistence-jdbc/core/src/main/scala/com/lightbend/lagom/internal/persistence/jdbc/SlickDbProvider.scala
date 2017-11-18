/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import javax.naming.InitialContext
import javax.sql.DataSource

import com.typesafe.config.Config
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor

object SlickDbProvider {

  def apply(dataSource: DataSource, config: Config): JdbcBackend.DatabaseDef = {

    val asyncExecConfig = new AsyncExecutorConfig(config)

    Database.forDataSource(
      ds = dataSource,
      maxConnections = Option(asyncExecConfig.maxConnections),
      executor = AsyncExecutor(
        name = "AsyncExecutor.default",
        minThreads = asyncExecConfig.minConnections,
        maxThreads = asyncExecConfig.numThreads,
        queueSize = asyncExecConfig.queueSize,
        maxConnections = asyncExecConfig.maxConnections
      )
    )
  }
}

class AsyncExecutorConfig(config: Config) {

  val numThreads: Int = config.getInt("numThreads")
  val minConnections: Int = config.getInt("minConnections")
  val maxConnections: Int = config.getInt("maxConnections")
  val queueSize: Int = config.getInt("queueSize")

  override def toString: String = s"AsyncExecutorConfig($numThreads, $minConnections, $maxConnections, $queueSize)"
}
