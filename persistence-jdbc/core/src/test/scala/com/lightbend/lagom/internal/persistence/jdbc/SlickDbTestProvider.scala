/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import javax.naming.InitialContext
import javax.sql.DataSource

import com.typesafe.config.ConfigFactory

object SlickDbTestProvider {

  /** Builds Slick Database (with AsyncExecutor) and bind it as JNDI resource for test purposes  */
  def buildAndBindSlickDb(dataSource: DataSource) = {

    val slickDb =
      SlickDbProvider(
        dataSource,
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

    new InitialContext().rebind("DefaultDB", slickDb)
  }

}
