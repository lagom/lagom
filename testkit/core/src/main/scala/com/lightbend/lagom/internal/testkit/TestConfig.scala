/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import com.typesafe.config.{ Config, ConfigFactory }

import scala.util.Try

private[lagom] object TestConfig {
  private lazy val defaults = ConfigFactory.load()

  private def getConfig(key: String, fallbackValue: String): String =
    s"""$key = "${Try(defaults.getString(key)).getOrElse(fallbackValue)}""""

  private lazy val values =
    s"""
       |${getConfig("db.default.driver", "org.h2.Driver")}
       |${getConfig("db.default.url", "jdbc:h2:mem:service-test")}
       |
       |${getConfig("jdbc-defaults.slick.profile", "slick.jdbc.H2Profile$")}
       |
       |jdbc-journal.slick.profile = $${?jdbc-defaults.slick.profile}
       |jdbc-read-journal.slick.profile = $${?jdbc-defaults.slick.profile}
       |jdbc-snapshot-store.slick.profile = $${?jdbc-defaults.slick.profile}
       |lagom.persistence.read-side.jdbc.slick.profile = $${?jdbc-defaults.slick.profile}
       |
       |akka.persistence.journal.plugin = jdbc-journal
       |akka.persistence.snapshot-store.plugin = jdbc-snapshot-store
    """.stripMargin

  lazy val JdbcConfig: Config = ConfigFactory.parseString(values).resolve()
}
