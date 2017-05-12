/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import play.api.{ Configuration, Environment }

object CassandraConsistencyValidator {

  def validate(configuration: Configuration, environment: Environment): Boolean = {
    val journalConfig: Option[Configuration] = configuration.getConfig("cassandra-journal")

    CassandraConsistencyConfig.load(journalConfig.get).exists {
      case CassandraConsistencyConfig("QUORUM", "QUORUM", Some(rf)) if rf >= 3 => true
      case _ => false
    }
  }

}

case class CassandraConsistencyConfig(
  readConsistency:   String,
  writeConsistency:  String,
  replicationFactor: Option[Int]
)

object CassandraConsistencyConfig {
  def load(configuration: Configuration): Option[CassandraConsistencyConfig] = {
    for {
      rc <- configuration.getString("read-consistency")
      wc <- configuration.getString("write-consistency")
    } yield {
      val rf = configuration.getInt("replication-factor")
      CassandraConsistencyConfig(rc, wc, rf)
    }
  }
}