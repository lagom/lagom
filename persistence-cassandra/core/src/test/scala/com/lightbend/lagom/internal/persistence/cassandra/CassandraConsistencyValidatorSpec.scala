/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import org.scalatest.{ FlatSpec, Matchers }
import play.api.{ Configuration, Environment, Mode }

class CassandraConsistencyValidatorSpec extends FlatSpec with Matchers {

  import CassandraConsistencyValidatorSpec._

  private val quorum3 = CassandraConsistencyConfig("QUORUM", "QUORUM", Some(3))
  private val quorum2 = quorum3.copy(replicationFactor = Some(2))

  behavior of "CassandraConsistencyValidator"

  it should "(for APC-journal) accept a read/write QUORUM with replication-factor >= 3" in {
    val config = Configuration.from(asJournalConfig(quorum3))
    CassandraConsistencyValidator.validateWriteSide(config) should be(true)
  }

  it should "(for APC-journal) deny a read/write QUORUM with replication-factor under 3" in {
    val config = Configuration.from(asJournalConfig(quorum2))
    CassandraConsistencyValidator.validateWriteSide(config) should be(false)
  }

  it should "(for Lagom's read-side) accept a read/write QUORUM with replication-factor >= 3" in {
    val config = Configuration.from(asReadSideConfig(quorum3))
    CassandraConsistencyValidator.validateReadSide(config) should be(true)
  }
  it should "(for Lagom's read-side) deny a read/write QUORUM with replication-factor under 3" in {
    val config = Configuration.from(asReadSideConfig(quorum2))
    CassandraConsistencyValidator.validateReadSide(config) should be(false)
  }

}

object CassandraConsistencyValidatorSpec {
  def asReadSideConfig(cassandraConsistencyConfig: CassandraConsistencyConfig): Map[String, Any] = {
    asTypeSafeConfig("lagom.persistence.read-side.cassandra", cassandraConsistencyConfig)
  }

  def asJournalConfig(cassandraConsistencyConfig: CassandraConsistencyConfig): Map[String, Any] = {
    asTypeSafeConfig("cassandra-journal", cassandraConsistencyConfig)
  }

  private def asTypeSafeConfig(prefix: String, cassandraConsistencyConfig: CassandraConsistencyConfig): Map[String, Any] = {
    Map(
      s"$prefix.write-consistency" -> cassandraConsistencyConfig.writeConsistency,
      s"$prefix.read-consistency" -> cassandraConsistencyConfig.readConsistency
    ) ++ cassandraConsistencyConfig.replicationFactor.map { rf =>
        Map(s"$prefix.replication-factor" -> rf)
      }.getOrElse(Map.empty[String, Any])
  }
}