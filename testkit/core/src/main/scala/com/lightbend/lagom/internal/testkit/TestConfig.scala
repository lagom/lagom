/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

private[lagom] object TestConfig {
  def cassandraConfig(keyspacePrefix: String, cassandraPort: Int): Map[String, AnyRef] = Map(
    "cassandra-journal.session-provider" -> "akka.persistence.cassandra.ConfigSessionProvider",
    "cassandra-snapshot-store.session-provider" -> "akka.persistence.cassandra.ConfigSessionProvider",
    "lagom.persistence.read-side.cassandra.session-provider" -> "akka.persistence.cassandra.ConfigSessionProvider",
    "akka.persistence.journal.plugin" -> "cassandra-journal",
    "akka.persistence.snapshot-store.plugin" -> "cassandra-snapshot-store",
    "cassandra-journal.port" -> cassandraPort.toString,
    "cassandra-journal.keyspace" -> keyspacePrefix,
    "cassandra-journal.contact-points.0" -> "127.0.0.1",
    "cassandra-snapshot-store.port" -> cassandraPort.toString,
    "cassandra-snapshot-store.keyspace" -> keyspacePrefix,
    "cassandra-snapshot-store.contact-points.0" -> "127.0.0.1",
    "lagom.persistence.read-side.cassandra.port" -> cassandraPort.toString,
    "lagom.persistence.read-side.cassandra.keyspace" -> s"${keyspacePrefix}_read",
    "lagom.persistence.read-side.cassandra.contact-points.0" -> "127.0.0.1",

    "cassandra-query-journal.eventual-consistency-delay" -> "2s",
    "akka.test.single-expect-default" -> "5s"
  )

  lazy val JdbcConfig: Map[String, AnyRef] = Map(
    "akka.persistence.journal.plugin" -> "jdbc-journal",
    "akka.persistence.snapshot-store.plugin" -> "jdbc-snapshot-store"
  )
}
