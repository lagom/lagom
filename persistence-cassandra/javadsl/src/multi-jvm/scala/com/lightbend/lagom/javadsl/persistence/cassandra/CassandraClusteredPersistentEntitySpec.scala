/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra

import java.io.File
import java.util.concurrent.CompletionStage

import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig.cassandraConfig
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.javadsl.persistence.multinode.{ AbstractClusteredPersistentEntityConfig, AbstractClusteredPersistentEntitySpec }
import com.lightbend.lagom.javadsl.persistence.{ ReadSideProcessor, TestEntityReadSide }
import com.typesafe.config.Config

object CassandraClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {
  override def additionalCommonConfig(databasePort: Int): Config =
    cassandraConfig("ClusteredPersistentEntitySpec", databasePort)
}

class CassandraClusteredPersistentEntitySpecMultiJvmNode1 extends CassandraClusteredPersistentEntitySpec
class CassandraClusteredPersistentEntitySpecMultiJvmNode2 extends CassandraClusteredPersistentEntitySpec
class CassandraClusteredPersistentEntitySpecMultiJvmNode3 extends CassandraClusteredPersistentEntitySpec

class CassandraClusteredPersistentEntitySpec extends AbstractClusteredPersistentEntitySpec(CassandraClusteredPersistentEntityConfig) {

  import CassandraClusteredPersistentEntityConfig._

  override protected def atStartup() {
    runOn(node1) {
      val cassandraDirectory = new File("target/" + system.name)
      CassandraLauncher.start(cassandraDirectory, "lagom-test-embedded-cassandra.yaml", clean = true, port = databasePort)
      awaitPersistenceInit(system)
    }
    enterBarrier("cassandra-started")

    super.atStartup()
  }

  override protected def afterTermination() {
    super.afterTermination()

    CassandraLauncher.stop()
  }

  def testEntityReadSide = injector.instanceOf[TestEntityReadSide]

  override protected def getAppendCount(id: String): CompletionStage[java.lang.Long] =
    testEntityReadSide.getAppendCount(id)

  override protected def readSideProcessor: Class[_ <: ReadSideProcessor[Evt]] = classOf[TestEntityReadSide.TestEntityReadSideProcessor]
}

