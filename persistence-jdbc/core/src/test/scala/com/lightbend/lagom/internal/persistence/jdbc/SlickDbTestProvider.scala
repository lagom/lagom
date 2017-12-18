/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import play.api.db.Databases
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import scala.util.Random

object SlickDbTestProvider {

  private val JNDIName = "DefaultDS"
  private val JNDIDBName = "DefaultDB"

  private val AsyncExecConfig = new AsyncExecutorConfig {
    override val numThreads: Int = 20
    override val minConnections: Int = 20
    override val maxConnections: Int = 100
    override val queueSize: Int = 100
  }

  /** Builds Slick Database (with AsyncExecutor) and bind it as JNDI resource for test purposes  */
  def buildAndBindSlickDb(baseName: String, lifecycle: ApplicationLifecycle): Unit = {
    val dbName = s"${baseName}_${Random.alphanumeric.take(8).mkString}"
    val db = Databases.inMemory(dbName, config = Map("jndiName" -> JNDIName))
    lifecycle.addStopHook(() => Future.successful(db.shutdown()))

    SlickDbProvider.buildAndBindSlickDatabase(db, AsyncExecConfig, JNDIDBName, lifecycle)
  }

}
