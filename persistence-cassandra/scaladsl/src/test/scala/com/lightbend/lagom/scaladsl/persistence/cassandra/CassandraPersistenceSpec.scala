/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.ActorSystem
import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.cluster.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig._
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.persistence.PersistenceSpec
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

abstract class CassandraPersistenceSpec private (actorSystemFactory: () => ActorSystem)
    extends ActorSystemSpec(actorSystemFactory) {
  def this(testName: String, config: Config, jsonSerializerRegistry: JsonSerializerRegistry) = {

    this(
      () => {
        // first start Cassandra and bind the necessary ports
        val cassandraDirectory = new File("target/" + testName)
        CassandraLauncher.start(cassandraDirectory, "lagom-test-embedded-cassandra.yaml", clean = true, port = 0)

        // start the ActorSystem
        // note that we first need to bind the Cassandra port and then pass it to the ActorSystem config
        // this is needed to allow the Cassandra plugin to connected to the randomly selected port
        ActorSystem(
          testName,
          ActorSystemSetup(
            BootstrapSetup(
              config
                .withFallback(cassandraConfig(testName, CassandraLauncher.randomPort))
            ),
            JsonSerializerRegistry.serializationSetupFor(jsonSerializerRegistry)
          )
        )
      }
    )
  }

  def this(config: Config, jsonSerializerRegistry: JsonSerializerRegistry) =
    this(PersistenceSpec.testNameFromCallStack(classOf[CassandraPersistenceSpec]), config, jsonSerializerRegistry)

  def this(jsonSerializerRegistry: JsonSerializerRegistry) = this(ConfigFactory.empty(), jsonSerializerRegistry)

  override def beforeAll(): Unit = {
    super.beforeAll()
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
