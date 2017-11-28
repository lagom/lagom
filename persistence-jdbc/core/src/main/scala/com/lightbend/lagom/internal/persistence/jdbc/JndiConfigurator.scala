/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import javax.naming.InitialContext

import com.typesafe.config.Config
import play.api.db.DBApi

import scala.util.Try

private[lagom] trait JndiConfigurator

private[lagom] object JndiConfigurator {

  def apply(dbApi: DBApi, config: Config): JndiConfigurator = new JndiConfiguratorImpl(dbApi, config)

  private final class JndiConfiguratorImpl(dbApi: DBApi, config: Config) extends JndiConfigurator {

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
      }
    }
  }

}
