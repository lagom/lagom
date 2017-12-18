/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import javax.naming.InitialContext

import com.typesafe.config.ConfigFactory
import play.api.db.Databases
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import scala.util.Random

object SlickDbTestProvider {

  private val JNDIName = "DefaultDS"
  private val JNDIDBName = "DefaultDB"

  /** Builds Slick Database (with AsyncExecutor) and bind it as JNDI resource for test purposes  */
  def buildAndBindSlickDb(baseName: String, lifecycle: ApplicationLifecycle): Unit = {
    val dbName = s"${baseName}_${Random.alphanumeric.take(8).mkString}"
    val db = Databases.inMemory(dbName, config = Map("jndiName" -> JNDIName))
    lifecycle.addStopHook(() => Future.successful(db.shutdown()))

    val slickDb =
      SlickDbProvider(
        db.dataSource,
        ConfigFactory.parseString(
          """
            |{
            |  queueSize = 100
            |  numThreads = 20
            |  minConnections = 20
            |  maxConnections = 100
            |}
          """.stripMargin
        )
      )

    val context = new InitialContext()

    context.bind(JNDIDBName, slickDb)
    // Unbind it again when the test tears down
    lifecycle.addStopHook { () =>
      context.unbind(JNDIDBName)
      slickDb.shutdown
    }
  }

}
