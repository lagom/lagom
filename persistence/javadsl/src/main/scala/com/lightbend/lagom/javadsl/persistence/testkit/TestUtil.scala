/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.testkit

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.testkit.TestProbe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

@deprecated("Use com.lightbend.lagom.javadsl.persistence.cassandra.testkit.TestUtil instead", "1.2.0")
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
}

trait AbstractTestUtil {
  def clusterConfig(): Config = ConfigFactory.parseString(s"""
    akka.actor.provider = akka.cluster.ClusterActorRefProvider
    akka.remote.netty.tcp.port = 0
    akka.remote.netty.tcp.hostname = 127.0.0.1
    """)

  def awaitPersistenceInit(system: ActorSystem): Unit = {
    val probe = TestProbe()(system)
    val log = LoggerFactory.getLogger(getClass)
    val t0 = System.nanoTime()
    var n = 0
    probe.within(45.seconds) {
      probe.awaitAssert {
        n += 1
        system.actorOf(Props[TestUtil.AwaitPersistenceInit], "persistenceInit" + n).tell("hello", probe.ref)
        probe.expectMsg(5.seconds, "hello")
        log.debug("awaitPersistenceInit took {} ms {}", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0), system.name)
      }
    }
  }

}
