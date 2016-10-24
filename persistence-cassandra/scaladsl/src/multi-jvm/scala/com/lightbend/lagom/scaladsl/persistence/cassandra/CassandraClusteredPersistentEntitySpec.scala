package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil
import com.lightbend.lagom.scaladsl.persistence.multinode.{AbstractClusteredPersistentEntityConfig, AbstractClusteredPersistentEntitySpec}
import com.typesafe.config.Config

import scala.concurrent.Future

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

  def testEntityReadSide = injector.instanceOf[TestEntityReadSide]

  override protected def getAppendCount(id: String): Future[Long] =
    testEntityReadSide.getAppendCount(id)

  override protected def readSideProcessor: Class[_ <: ReadSideProcessor[Evt]] = classOf[TestEntityReadSide.TestEntityReadSideProcessor]
}

