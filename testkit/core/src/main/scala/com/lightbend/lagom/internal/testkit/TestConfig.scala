/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import com.typesafe.config.{ Config, ConfigFactory }

private[lagom] object TestConfig {
  lazy val JdbcConfig: Config = ConfigFactory.parseString(
    """
      |jdbc-journal.slick.profile = ${?jdbc-defaults.slick.profile}
      |jdbc-read-journal.slick.profile = ${?jdbc-defaults.slick.profile}
      |jdbc-snapshot-store.slick.profile = ${?jdbc-defaults.slick.profile}
      |lagom.persistence.read-side.jdbc.slick.profile = ${?jdbc-defaults.slick.profile}
      |
      |akka.persistence.journal.plugin = jdbc-journal
      |akka.persistence.snapshot-store.plugin = jdbc-snapshot-store
    """.stripMargin
  ).resolve()
}
