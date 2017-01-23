package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.ActorSystem
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.{ActorMaterializer, Materializer}
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil
import com.lightbend.lagom.scaladsl.persistence.multinode.{AbstractClusteredPersistentEntityConfig, AbstractClusteredPersistentEntitySpec}
import com.typesafe.config.Config
import play.api.Configuration
import play.api.inject.DefaultApplicationLifecycle

import scala.concurrent.{ExecutionContext, Future}
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

class CassandraClusteredPersistentEntitySpec extends AbstractClusteredPersistentEntitySpec(CassandraClusteredPersistentEntityConfig) {

  import CassandraClusteredPersistentEntityConfig._

  override protected def atStartup() {
    runOn(node1) {
      val cassandraDirectory = new File("target/" + system.name)
      CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, clean = true, port = databasePort)
      TestUtil.awaitPersistenceInit(system)
    }
    enterBarrier("cassandra-started")

    super.atStartup()
  }

  override protected def afterTermination() {
    super.afterTermination()

    CassandraLauncher.stop()
  }

  lazy val defaultApplicationLifecycle = new DefaultApplicationLifecycle

  override lazy val components: CassandraPersistenceComponents =
    new CassandraPersistenceComponents {
      override def actorSystem: ActorSystem = system
      override def executionContext: ExecutionContext = system.dispatcher
      override def materializer: Materializer = ActorMaterializer()(system)
      override def configuration: Configuration = Configuration(system.settings.config)
      override def serviceLocator: ServiceLocator = NoServiceLocator
      override def jsonSerializerRegistry: JsonSerializerRegistry = ???
    }

  def testEntityReadSide = new TestEntityReadSide(components.actorSystem, components.cassandraSession)

  override protected def getAppendCount(id: String): Future[Long] =
    testEntityReadSide.getAppendCount(id)

  override protected def readSideProcessor: () => ReadSideProcessor[Evt] =
    () => new TestEntityReadSide.TestEntityReadSideProcessor(system, components.cassandraReadSide, components.cassandraSession)
}

