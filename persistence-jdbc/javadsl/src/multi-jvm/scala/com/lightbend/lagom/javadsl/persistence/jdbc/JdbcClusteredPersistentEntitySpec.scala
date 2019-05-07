/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import java.util.concurrent.CompletionStage

import com.lightbend.lagom.internal.javadsl.persistence.jdbc.SlickProvider
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor
import com.lightbend.lagom.javadsl.persistence.jdbc.testkit.TestUtil
import com.lightbend.lagom.javadsl.persistence.multinode.AbstractClusteredPersistentEntityConfig
import com.lightbend.lagom.javadsl.persistence.multinode.AbstractClusteredPersistentEntitySpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.tools.Server
import play.api.db.DBApi

object JdbcClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {
  override def additionalCommonConfig(databasePort: Int): Config = ConfigFactory.parseString(
    s"""
      db.default.driver=org.h2.Driver
      db.default.url="jdbc:h2:tcp://localhost:$databasePort/mem:JdbcClusteredPersistentEntitySpec"
    """
  )
}

class JdbcClusteredPersistentEntitySpecMultiJvmNode1 extends JdbcClusteredPersistentEntitySpec
class JdbcClusteredPersistentEntitySpecMultiJvmNode2 extends JdbcClusteredPersistentEntitySpec
class JdbcClusteredPersistentEntitySpecMultiJvmNode3 extends JdbcClusteredPersistentEntitySpec

class JdbcClusteredPersistentEntitySpec
    extends AbstractClusteredPersistentEntitySpec(JdbcClusteredPersistentEntityConfig) {

  import JdbcClusteredPersistentEntityConfig._

  var h2: Server = _

  protected override def atStartup() {
    runOn(node1) {
      h2 = Server.createTcpServer("-tcpPort", databasePort.toString).start()

    }

    enterBarrier("h2-started")

    super.atStartup()
  }

  protected override def afterTermination() {
    super.afterTermination()

    Option(h2).foreach(_.stop())
  }

  def testEntityReadSide = injector.instanceOf[JdbcTestEntityReadSide]

  protected override def getAppendCount(id: String): CompletionStage[java.lang.Long] =
    testEntityReadSide.getAppendCount(id)

  protected override def readSideProcessor: Class[_ <: ReadSideProcessor[Evt]] =
    classOf[JdbcTestEntityReadSide.TestEntityReadSideProcessor]
}
