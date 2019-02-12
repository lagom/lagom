/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.testkit

import scala.collection.JavaConverters._
import com.typesafe.config.{ Config, ConfigFactory }

private[lagom] object PersistenceTestConfig {
  lazy val ClusterConfigMap: Map[String, AnyRef] = Map(
    "akka.actor.provider" -> "akka.cluster.ClusterActorRefProvider",

    "akka.remote.netty.tcp.hostname" -> "127.0.0.1",
    "akka.remote.netty.tcp.port" -> "0",

    "lagom.cluster.join-self" -> "on",
    "lagom.cluster.bootstrap.enabled" -> "off"
  )

  lazy val ClusterConfig: Config = ConfigFactory.parseMap(ClusterConfigMap.asJava)

  def cassandraConfigMap(keyspacePrefix: String, cassandraPort: Int): Map[String, AnyRef] = Map(
    "akka.persistence.journal.plugin" -> "cassandra-journal",
    "akka.persistence.snapshot-store.plugin" -> "cassandra-snapshot-store",

    "akka.test.single-expect-default" -> "5s",

    "cassandra-journal.contact-points.0" -> "127.0.0.1",
    "cassandra-journal.keyspace" -> keyspacePrefix,
    "cassandra-journal.port" -> cassandraPort.toString,
    "cassandra-journal.session-provider" -> "akka.persistence.cassandra.ConfigSessionProvider",

    "cassandra-query-journal.eventual-consistency-delay" -> "2s",

    "cassandra-snapshot-store.contact-points.0" -> "127.0.0.1",
    "cassandra-snapshot-store.keyspace" -> keyspacePrefix,
    "cassandra-snapshot-store.port" -> cassandraPort.toString,
    "cassandra-snapshot-store.session-provider" -> "akka.persistence.cassandra.ConfigSessionProvider",

    "lagom.persistence.read-side.cassandra.contact-points.0" -> "127.0.0.1",
    "lagom.persistence.read-side.cassandra.keyspace" -> s"${keyspacePrefix}_read",
    "lagom.persistence.read-side.cassandra.port" -> cassandraPort.toString,
    "lagom.persistence.read-side.cassandra.session-provider" -> "akka.persistence.cassandra.ConfigSessionProvider"
  )

  def cassandraConfig(keyspacePrefix: String, cassandraPort: Int): Config =
    ConfigFactory.parseMap(cassandraConfigMap(keyspacePrefix, cassandraPort).asJava)

  lazy val JdbcConfigMap: Map[String, AnyRef] = Map(
    "akka.persistence.journal.plugin" -> "jdbc-journal",
    "akka.persistence.snapshot-store.plugin" -> "jdbc-snapshot-store"
  )

  lazy val JdbcConfig: Config = ConfigFactory.parseMap(JdbcConfigMap.asJava)
}
