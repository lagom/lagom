/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.jdbc

import akka.Done
import akka.actor.CoordinatedShutdown
import play.api.db.Databases

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

object SlickDbTestProvider {
  private val JNDIName   = "DefaultDS"
  private val JNDIDBName = "DefaultDB"

  private val AsyncExecConfig: AsyncExecutorConfig = new AsyncExecutorConfig {
    override val numThreads: Int         = 20
    override val minConnections: Int     = 20
    override val maxConnections: Int     = 20
    override val queueSize: Int          = 100
    override def registerMbeans: Boolean = false
  }

  /** Builds Slick Database (with AsyncExecutor) and bind it as JNDI resource for test purposes  */
  def buildAndBindSlickDb(baseName: String, coordinatedShutdown: CoordinatedShutdown)(
      implicit executionContext: ExecutionContext
  ): Unit = {
    val dbName = s"${baseName}_${Random.alphanumeric.take(8).mkString}"
    val db     = Databases.inMemory(dbName, config = Map("jndiName" -> JNDIName))

    SlickDbProvider.buildAndBindSlickDatabase(db, AsyncExecConfig, JNDIDBName, coordinatedShutdown)
  }
}
