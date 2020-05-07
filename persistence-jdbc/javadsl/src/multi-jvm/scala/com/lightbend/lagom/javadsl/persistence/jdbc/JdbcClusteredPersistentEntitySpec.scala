/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jdbc

import java.util.concurrent.CompletionStage

import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.javadsl.persistence.multinode.AbstractClusteredPersistentEntityConfig
import com.lightbend.lagom.javadsl.persistence.multinode.AbstractClusteredPersistentEntityConfig.Ports
import com.lightbend.lagom.javadsl.persistence.multinode.AbstractClusteredPersistentEntityConfig.Ports.SpecPorts
import com.lightbend.lagom.javadsl.persistence.multinode.AbstractClusteredPersistentEntitySpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.tools.Server

object JdbcClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {

  override def specPorts: SpecPorts = Ports.jdbcSpecPorts

  override def additionalCommonConfig: Config = ConfigFactory.parseString(
    s"""
      db.default.driver=org.h2.Driver
      db.default.url="jdbc:h2:tcp://localhost:${specPorts.database}/mem:JdbcClusteredPersistentEntitySpec"
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
      h2 = Server.createTcpServer("-tcpPort", specPorts.database.toString, "-ifNotExists").start()
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
