/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra.testkit

import akka.persistence.PersistentActor
import com.lightbend.lagom.scaladsl.persistence.testkit.AbstractTestUtil
import com.typesafe.config.{ Config, ConfigFactory }

object TestUtil extends AbstractTestUtil {

  def persistenceConfig(testName: String, cassandraPort: Int, useServiceLocator: Boolean) = ConfigFactory.parseString(s"""
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
    """).withFallback(clusterConfig())

  class AwaitPersistenceInit extends PersistentActor {
    def persistenceId: String = self.path.name

    def receiveRecover: Receive = {
      case _ =>
    }

    def receiveCommand: Receive = {
      case msg =>
        persist(msg) { _ =>
          sender() ! msg
          context.stop(self)
        }
    }
  }

  def persistenceConfig(testName: String, cassandraPort: Int): Config = {
    persistenceConfig(testName, cassandraPort, useServiceLocator = false)
  }

}
