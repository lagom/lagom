/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.testkit

import scala.collection.JavaConverters._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

private[lagom] object PersistenceTestConfig {
  lazy val BasicConfigMap: Map[String, AnyRef] = Map(
    "lagom.akka.management.enabled" -> "off",
    "akka.actor.provider"           -> "local",
  )

  lazy val BasicConfig: Config = ConfigFactory.parseMap(BasicConfigMap.asJava)

  lazy val ClusterConfigMap: Map[String, AnyRef] =
    BasicConfigMap ++
      Map(
        "akka.actor.provider"                   -> "cluster",
        "akka.remote.artery.canonical.port"     -> "0",
        "akka.remote.artery.canonical.hostname" -> "127.0.0.1",
        // needed when users opt-out from Artery
        "akka.remote.classic.netty.tcp.port"            -> "0",
        "akka.remote.classic.netty.tcp.hostname"        -> "127.0.0.1",
        "lagom.cluster.join-self"                       -> "on",
        "lagom.cluster.exit-jvm-when-system-terminated" -> "off",
        "lagom.cluster.bootstrap.enabled"               -> "off"
      )

  lazy val ClusterConfig: Config = ConfigFactory.parseMap(ClusterConfigMap.asJava)

  /** Return the Cassandra config Map with the default Cluster settings */
  def cassandraConfigMap(keyspacePrefix: String, cassandraPort: Int): Map[String, AnyRef] =
    ClusterConfigMap ++ cassandraConfigMapOnly(keyspacePrefix, cassandraPort)

  /** Return the Cassandra config with the default Cluster settings */
  def cassandraConfig(keyspacePrefix: String, cassandraPort: Int): Config =
    ConfigFactory.parseMap(cassandraConfigMap(keyspacePrefix, cassandraPort).asJava)

  /**
   * Return the Cassandra config Map without the default Cluster settings
   * Specially useful for multi-jvm tests that configures the cluster manually
   */
  def cassandraConfigMapOnly(keyspacePrefix: String, cassandraPort: Int): Map[String, AnyRef] =
    Map(
      "akka.loglevel"                                          -> "INFO",
      "akka.persistence.journal.plugin"                        -> "cassandra-journal",
      "akka.persistence.snapshot-store.plugin"                 -> "cassandra-snapshot-store",
      "akka.test.single-expect-default"                        -> "5s",
      "cassandra-journal.contact-points.0"                     -> "127.0.0.1",
      "cassandra-journal.keyspace"                             -> keyspacePrefix,
      "cassandra-journal.port"                                 -> cassandraPort.toString,
      "cassandra-journal.session-provider"                     -> "akka.persistence.cassandra.ConfigSessionProvider",
      "cassandra-query-journal.eventual-consistency-delay"     -> "2s",
      "cassandra-snapshot-store.contact-points.0"              -> "127.0.0.1",
      "cassandra-snapshot-store.keyspace"                      -> keyspacePrefix,
      "cassandra-snapshot-store.port"                          -> cassandraPort.toString,
      "cassandra-snapshot-store.session-provider"              -> "akka.persistence.cassandra.ConfigSessionProvider",
      "lagom.persistence.read-side.cassandra.contact-points.0" -> "127.0.0.1",
      "lagom.persistence.read-side.cassandra.keyspace"         -> s"${keyspacePrefix}_read",
      "lagom.persistence.read-side.cassandra.port"             -> cassandraPort.toString,
      "lagom.persistence.read-side.cassandra.session-provider" -> "akka.persistence.cassandra.ConfigSessionProvider"
    )

  /**
   * Return the Cassandra config without the default Cluster settings
   * Specially useful for multi-jvm tests that configures the cluster manually
   */
  def cassandraConfigOnly(keyspacePrefix: String, cassandraPort: Int): Config =
    ConfigFactory.parseMap(cassandraConfigMapOnly(keyspacePrefix, cassandraPort).asJava)

  lazy val JdbcConfigMap: Map[String, AnyRef] =
    ClusterConfigMap ++
      Map(
        "akka.persistence.journal.plugin"        -> "jdbc-journal",
        "akka.persistence.snapshot-store.plugin" -> "jdbc-snapshot-store"
      )

  lazy val JdbcConfig: Config = ConfigFactory.parseMap(JdbcConfigMap.asJava)
}
