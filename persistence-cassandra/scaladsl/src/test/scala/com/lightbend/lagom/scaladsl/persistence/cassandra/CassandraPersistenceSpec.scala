/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.setup.ActorSystemSetup
import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.cluster.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig._
import com.lightbend.lagom.persistence.{ ActorSystemSpec, PersistenceSpec }
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.{ Config, ConfigFactory }

class CassandraPersistenceSpec private (system: ActorSystem) extends ActorSystemSpec(system) {

  def this(testName: String, config: Config, jsonSerializerRegistry: JsonSerializerRegistry) =
    this(ActorSystem(testName, ActorSystemSetup(
      BootstrapSetup(
        config
          .withFallback(cassandraConfig(testName, CassandraLauncher.randomPort))
          .withFallback(ClusterConfig)
      ),
      JsonSerializerRegistry.serializationSetupFor(jsonSerializerRegistry)
    )))

  def this(config: Config, jsonSerializerRegistry: JsonSerializerRegistry) = this(PersistenceSpec.getCallerName(getClass), config, jsonSerializerRegistry)

  def this(jsonSerializerRegistry: JsonSerializerRegistry) = this(ConfigFactory.empty(), jsonSerializerRegistry)

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
