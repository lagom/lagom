package com.lightbend.lagom.javadsl.persistence.jdbc

import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcTestEntityReadSide
import com.lightbend.lagom.scaladsl.persistence.multinode.{AbstractClusteredPersistentEntityConfig, AbstractClusteredPersistentEntitySpec}
import com.typesafe.config.{Config, ConfigFactory}
import org.h2.tools.Server

import scala.concurrent.Future

object JdbcClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {
  override def additionalCommonConfig(databasePort: Int): Config = ConfigFactory.parseString(
    s"""
      db.default.driver=org.h2.Driver
      db.default.url="jdbc:h2:tcp://localhost:$databasePort/mem:JdbcClusteredPersistentEntitySpec"
      lagom.serialization.play-json {
        serialization-registry = "com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry"
      }
    """
  )
}

class JdbcClusteredPersistentEntitySpecMultiJvmNode1 extends JdbcClusteredPersistentEntitySpec
class JdbcClusteredPersistentEntitySpecMultiJvmNode2 extends JdbcClusteredPersistentEntitySpec
class JdbcClusteredPersistentEntitySpecMultiJvmNode3 extends JdbcClusteredPersistentEntitySpec

class JdbcClusteredPersistentEntitySpec extends AbstractClusteredPersistentEntitySpec(JdbcClusteredPersistentEntityConfig) {

  import JdbcClusteredPersistentEntityConfig._

  var h2: Server = _

  override protected def atStartup(): Unit = {
    runOn(node1) {
      h2 = Server.createTcpServer("-tcpPort", databasePort.toString).start()

    }

    enterBarrier("h2-started")

    super.atStartup()
  }

  override protected def afterTermination(): Unit = {
    super.afterTermination()

    Option(h2).foreach(_.stop())
  }

  def testEntityReadSide = injector.instanceOf[JdbcTestEntityReadSide]

  override protected def getAppendCount(id: String): Future[Long] =
    testEntityReadSide.getAppendCount(id)

  override protected def readSideProcessor: Class[_ <: ReadSideProcessor[Evt]] =
    classOf[JdbcTestEntityReadSide.TestEntityReadSideProcessor]
}

