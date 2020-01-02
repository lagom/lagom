/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.jdbc

import akka.Done
import akka.actor.CoordinatedShutdown
import javax.naming.InitialContext
import javax.sql.DataSource
import com.typesafe.config.Config
import play.api.db.DBApi
import play.api.db.Database
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.JdbcBackend.{ Database => SlickDatabase }
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

private[lagom] object SlickDbProvider {
  def buildAndBindSlickDatabases(
      dbApi: DBApi,
      config: Config,
      coordinatedShutdown: CoordinatedShutdown
  )(implicit executionContext: ExecutionContext): Unit = {
    dbApi.databases().foreach { playDb =>
      val dbName = playDb.name

      // each configured DB having an async-executor section
      // has a Slick DB configured and bound to a JNDI name
      for {
        dbConfig        <- Try(config.getConfig(s"db.$dbName"))
        asyncExecConfig <- Try(AsyncExecutorConfig(dbConfig.getConfig("async-executor")))
      } yield {
        // a DB config with an async-executor is expected to have an associated jndiDbName
        // failing to do so will raise an exception
        val jndiDbName = dbConfig.getString("jndiDbName")

        buildAndBindSlickDatabase(playDb, asyncExecConfig, jndiDbName, coordinatedShutdown)
      }
    }
  }

  def buildAndBindSlickDatabase(
      playDb: Database,
      asyncExecConfig: AsyncExecutorConfig,
      jndiDbName: String,
      coordinatedShutdown: CoordinatedShutdown
  )(implicit executionContext: ExecutionContext): Unit = {
    val slickDb =
      buildSlickDatabase(
        // the data source as configured by Play
        playDb.dataSource,
        asyncExecConfig
      )

    bindSlickDatabase(jndiDbName, slickDb, coordinatedShutdown)
  }

  private def buildSlickDatabase(dataSource: DataSource, asyncExecConfig: AsyncExecutorConfig): DatabaseDef = {
    SlickDatabase.forDataSource(
      ds = dataSource,
      maxConnections = Option(asyncExecConfig.maxConnections),
      executor = AsyncExecutor(
        name = "AsyncExecutor.default",
        minThreads = asyncExecConfig.minConnections,
        maxThreads = asyncExecConfig.numThreads,
        queueSize = asyncExecConfig.queueSize,
        maxConnections = asyncExecConfig.maxConnections,
        registerMbeans = asyncExecConfig.registerMbeans
      )
    )
  }

  private def bindSlickDatabase(
      jndiDbName: String,
      slickDb: DatabaseDef,
      coordinatedShutdown: CoordinatedShutdown
  )(implicit executionContext: ExecutionContext): Unit = {
    val namingContext = new InitialContext()

    // we don't simply override a previously configured DB resource
    // if name is already in use, bind() method throws NameAlreadyBoundException
    namingContext.bind(jndiDbName, slickDb)

    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseServiceUnbind,
      s"unbind-slick-db-from-JNDI"
    ) { () =>
      namingContext.unbind(jndiDbName)
      Future.successful(Done)
    }

    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
      s"shutdown-managed-read-side-slick"
    ) { () =>
      slickDb.shutdown.map(_ => Done)
    }
  }
}

private[lagom] trait AsyncExecutorConfig {
  def numThreads: Int
  def minConnections: Int
  def maxConnections: Int
  def queueSize: Int
  def registerMbeans: Boolean
}

private[lagom] object AsyncExecutorConfig {
  def apply(config: Config): AsyncExecutorConfig = new AsyncExecutorConfigImpl(config)

  private final class AsyncExecutorConfigImpl(config: Config) extends AsyncExecutorConfig {
    val numThreads: Int         = config.getInt("numThreads")
    val minConnections: Int     = config.getInt("minConnections")
    val maxConnections: Int     = config.getInt("maxConnections")
    val queueSize: Int          = config.getInt("queueSize")
    val registerMbeans: Boolean = config.getBoolean("registerMbeans")

    override def toString: String =
      s"AsyncExecutorConfig($numThreads, $minConnections, $maxConnections, $queueSize, $registerMbeans)"
  }
}
