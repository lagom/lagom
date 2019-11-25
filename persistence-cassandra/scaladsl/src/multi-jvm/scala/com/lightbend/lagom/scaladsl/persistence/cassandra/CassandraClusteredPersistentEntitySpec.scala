/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.ActorSystem
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil
import com.lightbend.lagom.scaladsl.persistence.multinode.AbstractClusteredPersistentEntityConfig
import com.lightbend.lagom.scaladsl.persistence.multinode.AbstractClusteredPersistentEntitySpec
import com.typesafe.config.Config
import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.inject.DefaultApplicationLifecycle

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

object CassandraClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {
  override def additionalCommonConfig(databasePort: Int): Config =
    TestUtil.persistenceConfig("CassandraClusteredPersistentEntityConfig", databasePort)
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
      TestUtil.awaitPersistenceInit(system)
    }
    enterBarrier("cassandra-initialised")

    // Now make sure that sure the other node's persistence layers are warmed up
    runOn(node2, node3) {
      TestUtil.awaitPersistenceInit(system)
    }
    enterBarrier("cassandra-accessible")

    super.atStartup()
  }

  protected override def afterTermination() {
    super.afterTermination()

    CassandraLauncher.stop()
  }

  lazy val defaultApplicationLifecycle = new DefaultApplicationLifecycle

  override lazy val components: CassandraPersistenceComponents =
    new CassandraPersistenceComponents {
      override def actorSystem: ActorSystem           = system
      override def executionContext: ExecutionContext = system.dispatcher

      override def environment: Environment                       = Environment(new File("."), getClass.getClassLoader, Mode.Test)
      override def materializer: Materializer                     = ActorMaterializer()(system)
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
