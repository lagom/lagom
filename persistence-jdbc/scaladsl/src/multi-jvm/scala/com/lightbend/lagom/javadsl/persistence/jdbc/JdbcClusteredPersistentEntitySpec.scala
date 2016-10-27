package com.lightbend.lagom.scaladsl.persistence.jdbc

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.multinode.{ AbstractClusteredPersistentEntityConfig, AbstractClusteredPersistentEntitySpec }
import com.typesafe.config.{ Config, ConfigFactory }
import org.h2.tools.Server
import play.api.Configuration
import play.api.Environment
import play.api.db.HikariCPComponents
import play.api.inject.ApplicationLifecycle
import play.api.inject.DefaultApplicationLifecycle

object JdbcClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {
  override def additionalCommonConfig(databasePort: Int): Config = ConfigFactory.parseString(
    s"""
      db.default.driver=org.h2.Driver
      db.default.url="jdbc:h2:tcp://localhost:$databasePort/mem:JdbcClusteredPersistentEntitySpec"
      lagom.serialization.play-json {
        serialization-registry = "com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry"
      }
    """)
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
    defaultApplicationLifecycle.stop()

    Option(h2).foreach(_.stop())
  }

  lazy val defaultApplicationLifecycle = new DefaultApplicationLifecycle

  override lazy val components: JdbcPersistenceComponents =
    new JdbcPersistenceComponents with HikariCPComponents {
      override def actorSystem: ActorSystem = JdbcClusteredPersistentEntitySpec.this.system
      override def executionContext: ExecutionContext = system.dispatcher
      override lazy val materializer: Materializer = ActorMaterializer.create(system)
      override lazy val configuration: Configuration = Configuration(system.settings.config)
      override def environment: Environment = JdbcClusteredPersistentEntityConfig.environment
      override lazy val applicationLifecycle: ApplicationLifecycle = defaultApplicationLifecycle
    }

  lazy val jdbcTestEntityReadSide: JdbcTestEntityReadSide =
    new JdbcTestEntityReadSide(components.jdbcSession)

  override protected def getAppendCount(id: String): Future[Long] =
    jdbcTestEntityReadSide.getAppendCount(id)

  override protected def readSideProcessor: () => ReadSideProcessor[Evt] = {
    () => new JdbcTestEntityReadSide.TestEntityReadSideProcessor(components.jdbcReadSide)
  }
}

