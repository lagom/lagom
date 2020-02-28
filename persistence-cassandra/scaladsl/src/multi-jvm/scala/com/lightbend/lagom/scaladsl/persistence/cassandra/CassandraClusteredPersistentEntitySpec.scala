/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig.cassandraConfigOnly
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.multinode.AbstractClusteredPersistentEntityConfig
import com.lightbend.lagom.scaladsl.persistence.multinode.AbstractClusteredPersistentEntitySpec
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.Config
import play.api.inject.DefaultApplicationLifecycle
import play.api.Configuration
import play.api.Environment
import play.api.Mode

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object CassandraClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {
  override def additionalCommonConfig(databasePort: Int): Config = {
    cassandraConfigOnly("CassandraClusteredPersistentEntityConfig", databasePort)
      .withFallback(CassandraReadSideSpec.readSideConfig)
  }
}

class CassandraClusteredPersistentEntitySpecMultiJvmNode1 extends CassandraClusteredPersistentEntitySpec
class CassandraClusteredPersistentEntitySpecMultiJvmNode2 extends CassandraClusteredPersistentEntitySpec
class CassandraClusteredPersistentEntitySpecMultiJvmNode3 extends CassandraClusteredPersistentEntitySpec

class CassandraClusteredPersistentEntitySpec
    extends AbstractClusteredPersistentEntitySpec(CassandraClusteredPersistentEntityConfig) {
  import CassandraClusteredPersistentEntityConfig._

  protected override def atStartup(): Unit = {
    // On only one node (node1), start Cassandra & make sure the persistence layers have initialised
    // Node1 is also the only node where cassandra-journal.keyspace-autocreate isn't disabled
    runOn(node1) {
      val cassandraDirectory = new File("target/" + system.name)
      CassandraLauncher.start(
        cassandraDirectory,
        "lagom-test-embedded-cassandra.yaml",
        clean = true,
        port = databasePort
      )
      awaitPersistenceInit(system)
    }
    enterBarrier("cassandra-initialised")

    // Now make sure that sure the other node's persistence layers are warmed up
    runOn(node2, node3) {
      awaitPersistenceInit(system)
    }
    enterBarrier("cassandra-accessible")

    super.atStartup()
  }

  protected override def afterTermination(): Unit = {
    super.afterTermination()
    Await.ready(defaultApplicationLifecycle.stop(), shutdownTimeout)
    CassandraLauncher.stop()
  }

  lazy val defaultApplicationLifecycle = new DefaultApplicationLifecycle

  override lazy val components: CassandraPersistenceComponents =
    new CassandraPersistenceComponents {
      override def actorSystem: ActorSystem                 = system
      override def executionContext: ExecutionContext       = system.dispatcher
      override def coordinatedShutdown: CoordinatedShutdown = CoordinatedShutdown(actorSystem)

      override def environment: Environment                       = Environment(new File("."), getClass.getClassLoader, Mode.Test)
      override def materializer: Materializer                     = SystemMaterializer(system).materializer
      override def configuration: Configuration                   = Configuration(system.settings.config)
      override def serviceLocator: ServiceLocator                 = NoServiceLocator
      override def jsonSerializerRegistry: JsonSerializerRegistry = ???
    }

  def testEntityReadSide = new TestEntityReadSide(components.actorSystem, components.cassandraSession)

  protected override def getAppendCount(id: String): Future[Long] =
    testEntityReadSide.getAppendCount(id)

  protected override def readSideProcessor: () => ReadSideProcessor[Evt] =
    () =>
      new TestEntityReadSide.TestEntityReadSideProcessor(
        system,
        components.cassandraReadSide,
        components.cassandraSession
      )
}
