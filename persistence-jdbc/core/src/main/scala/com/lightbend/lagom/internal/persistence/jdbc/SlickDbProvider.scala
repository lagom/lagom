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

private[lagom] object SlickDbProvider {

  def apply(dataSource: DataSource, config: Config): JdbcBackend.DatabaseDef = {

    val asyncExecConfig = AsyncExecutorConfig(config)

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

private [lagom] trait AsyncExecutorConfig {
  def numThreads: Int
  def minConnections: Int
  def maxConnections: Int
  def queueSize: Int
}

private [lagom] object AsyncExecutorConfig {

  def apply(config: Config): AsyncExecutorConfig = new AsyncExecutorConfigImpl(config)

  private[lagom] class AsyncExecutorConfigImpl(config: Config) extends AsyncExecutorConfig {

    val numThreads: Int = config.getInt("numThreads")
    val minConnections: Int = config.getInt("minConnections")
    val maxConnections: Int = config.getInt("maxConnections")
    val queueSize: Int = config.getInt("queueSize")

    override def toString: String = s"AsyncExecutorConfig($numThreads, $minConnections, $maxConnections, $queueSize)"
  }
}
