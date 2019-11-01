/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig.cassandraConfigOnly
import com.lightbend.lagom.persistence.TolerateFailuresWhenRunningContinuousIntegration
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
  override def additionalCommonConfig(databasePort: Int): Config =
    cassandraConfigOnly("CassandraClusteredPersistentEntityConfig", databasePort)
}

class CassandraClusteredPersistentEntitySpecMultiJvmNode1 extends CassandraClusteredPersistentEntitySpec
class CassandraClusteredPersistentEntitySpecMultiJvmNode2 extends CassandraClusteredPersistentEntitySpec
class CassandraClusteredPersistentEntitySpecMultiJvmNode3 extends CassandraClusteredPersistentEntitySpec

class CassandraClusteredPersistentEntitySpec
    extends AbstractClusteredPersistentEntitySpec(CassandraClusteredPersistentEntityConfig)
    with TolerateFailuresWhenRunningContinuousIntegration {
  import CassandraClusteredPersistentEntityConfig._

  protected override def atStartup(): Unit = {
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
    enterBarrier("cassandra-started")

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
