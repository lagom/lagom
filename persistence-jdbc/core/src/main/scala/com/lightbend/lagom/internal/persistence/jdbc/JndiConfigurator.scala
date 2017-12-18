/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import javax.naming.InitialContext

import com.typesafe.config.Config
import play.api.db.DBApi
import play.api.inject.ApplicationLifecycle

import scala.util.Try

private[lagom] object JndiConfigurator {

  def apply(dbApi: DBApi, config: Config, lifecycle: ApplicationLifecycle): Unit = {

    val namingContext = new InitialContext()

    dbApi.databases().foreach { playDb =>

      val dbName = playDb.name

      // each configured DB having an async-executor section
      // is entitled to for Slick DB configured and bounded to a JNDI name
      for {
        dbConfig <- Try(config.getConfig(s"db.$dbName"))
        asyncExecConfig <- Try(dbConfig.getConfig("async-executor"))
      } yield {

        // a DB config with an async-executor is expected to have an associated jndiDbName
        // failing to do so will raise an exception
        val jndiDbName = dbConfig.getString("jndiDbName")

        val slickDb =
          SlickDbProvider(
            // the data source as configured by Play
            playDb.dataSource,
            asyncExecConfig
          )

        // we don't simply override a previously configure DB resource
        // if name is already in use, bind() method throws NameAlreadyBoundException
        namingContext.bind(jndiDbName, slickDb)

        lifecycle.addStopHook { () =>
          namingContext.unbind(jndiDbName)
          slickDb.shutdown
        }
      }
    }
  }

}
