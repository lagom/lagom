/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import com.typesafe.config.{ Config, ConfigFactory }

private[lagom] object TestConfig {
  private lazy val values =
    s"""
       |akka.persistence.journal.plugin = jdbc-journal
       |akka.persistence.snapshot-store.plugin = jdbc-snapshot-store
    """.stripMargin

  lazy val JdbcConfig: Config = ConfigFactory.parseString(values).resolve()
}
