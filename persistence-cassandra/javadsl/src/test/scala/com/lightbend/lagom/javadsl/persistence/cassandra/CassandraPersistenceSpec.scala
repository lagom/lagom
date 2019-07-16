/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra

import java.io.File

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig._
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.persistence.PersistenceSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class CassandraPersistenceSpec(system: ActorSystem) extends ActorSystemSpec(system) {

  def this(testName: String, config: Config) =
    this(
      ActorSystem(
        testName,
        config
          .withFallback(cassandraConfig(testName, CassandraLauncher.randomPort))
      )
    )

  def this(config: Config) = this(PersistenceSpec.getCallerName(getClass), config)

  def this() = this(ConfigFactory.empty())

  override def beforeAll(): Unit = {
    super.beforeAll()

    val cassandraDirectory = new File("target/" + system.name)
    CassandraLauncher.start(cassandraDirectory, "lagom-test-embedded-cassandra.yaml", clean = true, port = 0)
    awaitPersistenceInit(system)

    // Join ourselves - needed because the Cassandra offset store uses cluster startup task
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
  }

  override def afterAll(): Unit = {
    CassandraLauncher.stop()
    super.afterAll()
  }

}
