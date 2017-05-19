/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import play.api.Configuration

object CassandraConsistencyValidator {

  def validateWriteSide(configuration: Configuration): Seq[String] = {
    val journalConfig: Option[Configuration] = configuration.getConfig("cassandra-journal")
    if (!isValid(journalConfig)) {
      Seq("""Invalid consistency level or replication factor for "cassandra-journal".""")
    } else {
      Seq.empty[String]
    }
  }

  def validateReadSide(configuration: Configuration): Seq[String] = {
    val journalConfig: Option[Configuration] = configuration.getConfig("lagom.persistence.read-side.cassandra")
    if (!isValid(journalConfig)) {
      Seq("""Invalid consistency level or replication factor for "lagom.persistence.read-side.cassandra".""")
    } else {
      Seq.empty[String]
    }
  }

  private def isValid(configuration: Option[Configuration]) = {
    CassandraConsistencyConfig.load(configuration.get).exists {
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