/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.testkit

import com.lightbend.lagom.javadsl.persistence.testkit.AbstractTestUtil
import com.typesafe.config.{ Config, ConfigFactory }

/**
 * This lives in persistence rather than persistence-javadsl so that both persistence-cassandra-javadsl, and
 * testkit-javadsl can use it.
 */
private[lagom] object CassandraTestConfig extends AbstractTestUtil {
  def persistenceConfig(testName: String, cassandraPort: Int): Config = {
    ConfigFactory.parseString(
      s"""
      cassandra-journal.session-provider = akka.persistence.cassandra.ConfigSessionProvider
      cassandra-snapshot-store.session-provider = akka.persistence.cassandra.ConfigSessionProvider
      lagom.persistence.read-side.cassandra.session-provider = akka.persistence.cassandra.ConfigSessionProvider

      akka.persistence.journal.plugin = "cassandra-journal"
      akka.persistence.snapshot-store.plugin = "cassandra-snapshot-store"

      cassandra-journal {
       port = $cassandraPort
       keyspace = $testName
      }
      cassandra-snapshot-store {
       port = $cassandraPort
       keyspace = $testName
      }
      cassandra-query-journal.eventual-consistency-delay = 2s

      lagom.persistence.read-side.cassandra {
       port = $cassandraPort
       keyspace = ${testName}_read
      }

      akka.test.single-expect-default = 5s
   """
    ).withFallback(clusterConfig())
  }
}
