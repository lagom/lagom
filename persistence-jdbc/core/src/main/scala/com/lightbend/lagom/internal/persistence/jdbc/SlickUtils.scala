package com.lightbend.lagom.internal.persistence.jdbc

import javax.naming.InitialContext

import akka.persistence.jdbc.config.SlickConfiguration
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend._

object SlickUtils {

  def forDriverName(config: Config): JdbcProfile =
    DatabaseConfig.forConfig[JdbcProfile]("slick", config).profile

  def forConfig(config: Config, slickConfiguration: SlickConfiguration): Database = {
    slickConfiguration.jndiName
      .map(Database.forName(_, None))
      .orElse {
        slickConfiguration.jndiDbName.map(
          new InitialContext().lookup(_).asInstanceOf[Database]
        )
      }
      .getOrElse(Database.forConfig("slick.db", config))
  }

}
