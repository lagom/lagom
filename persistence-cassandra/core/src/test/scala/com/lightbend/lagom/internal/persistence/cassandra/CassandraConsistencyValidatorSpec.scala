package com.lightbend.lagom.internal.persistence.cassandra

import org.scalatest.{ FlatSpec, Matchers }
import play.api.{ Configuration, Environment, Mode }


class CassandraConsistencyValidatorSpec extends FlatSpec with Matchers {

  behavior of "CassandraConsistencyValidator"

  it should "(for APC-journal in Prod) accept a read/write QUORUM with replication-factor >= 3" in {
    val config = Configuration.from(Map(
      "cassandra-journal.write-consistency" -> "QUORUM",
      "cassandra-journal.read-consistency" -> "QUORUM",
      "cassandra-journal.replication-factor" -> 3
    ))

    CassandraConsistencyValidator.validate(config, Environment.simple(mode = Mode.Prod)) should be (true)

  }
  it should "(for APC-journal in Prod) deny a read/write QUORUM with replication-factor under 3" in {
    val config = Configuration.from(Map(
      "cassandra-journal.write-consistency" -> "QUORUM",
      "cassandra-journal.read-consistency" -> "QUORUM",
      "cassandra-journal.replication-factor" -> 2
    ))
    CassandraConsistencyValidator.validate(config, Environment.simple(mode = Mode.Prod)) should be (false)
  }

}
