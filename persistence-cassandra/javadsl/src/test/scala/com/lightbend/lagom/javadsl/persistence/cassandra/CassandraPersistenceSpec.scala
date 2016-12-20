/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.io.File

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.persistence.{ ActorSystemSpec, PersistenceSpec }
import com.lightbend.lagom.javadsl.persistence.cassandra.testkit.TestUtil
import com.typesafe.config.{ Config, ConfigFactory }

class CassandraPersistenceSpec(system: ActorSystem) extends ActorSystemSpec(system) {

  def this(testName: String, config: Config) =
    this(ActorSystem(testName, config.withFallback(TestUtil.persistenceConfig(
      testName,
      CassandraLauncher.randomPort
    ))))

  def this(config: Config) = this(PersistenceSpec.getCallerName(getClass), config)

  def this() = this(ConfigFactory.empty())

  override def beforeAll(): Unit = {
    super.beforeAll()

    val cassandraDirectory = new File("target/" + system.name)
    CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, clean = true, port = 0)
    TestUtil.awaitPersistenceInit(system)

    // Join ourselves - needed because the Cassandra offset store uses cluster startup task
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
  }

  override def afterAll(): Unit = {
    CassandraLauncher.stop()
    super.afterAll()
  }

}
