/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.actor.setup.ActorSystemSetup
import akka.cluster.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.persistence.{ ActorSystemSpec, PersistenceSpec }
import com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.{ Config, ConfigFactory }

class CassandraPersistenceSpec private (system: ActorSystem) extends ActorSystemSpec(system) {

  def this(testName: String, config: Config, jsonSerializerRegistry: JsonSerializerRegistry) =
    this(ActorSystem(testName, ActorSystemSetup(
      BootstrapSetup(config.withFallback(TestUtil.persistenceConfig(
        testName,
        CassandraLauncher.randomPort
      ))),
      JsonSerializerRegistry.serializationSetupFor(jsonSerializerRegistry)
    )))

  def this(config: Config, jsonSerializerRegistry: JsonSerializerRegistry) = this(PersistenceSpec.getCallerName(getClass), config, jsonSerializerRegistry)

  def this(jsonSerializerRegistry: JsonSerializerRegistry) = this(ConfigFactory.empty(), jsonSerializerRegistry)

  override def beforeAll(): Unit = {
    super.beforeAll()

    val cassandraDirectory = new File("target/" + system.name)
    CassandraLauncher.start(cassandraDirectory, "lagom-test-embedded-cassandra.yaml", clean = true, port = 0)
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
