/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.multinode.{ AbstractClusteredPersistentEntityConfig, AbstractClusteredPersistentEntitySpec }
import com.lightbend.lagom.scaladsl.persistence.{ ReadSideProcessor, TestEntitySerializerRegistry }
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.{ Config, ConfigFactory }
import org.h2.tools.Server
import play.api.{ Configuration, Environment }
import play.api.db.HikariCPComponents
import play.api.inject.{ ApplicationLifecycle, DefaultApplicationLifecycle }

import scala.concurrent.{ ExecutionContext, Future }

object SlickClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {
  override def additionalCommonConfig(databasePort: Int): Config = ConfigFactory.parseString(
    s"""
      db.default.driver=org.h2.Driver
      db.default.url="jdbc:h2:tcp://localhost:$databasePort/mem:JdbcClusteredPersistentEntitySpec"
    """
  )
}

class SlickClusteredPersistentEntitySpecMultiJvmNode1 extends SlickClusteredPersistentEntitySpec
class SlickClusteredPersistentEntitySpecMultiJvmNode2 extends SlickClusteredPersistentEntitySpec
class SlickClusteredPersistentEntitySpecMultiJvmNode3 extends SlickClusteredPersistentEntitySpec

class SlickClusteredPersistentEntitySpec
  extends AbstractClusteredPersistentEntitySpec(SlickClusteredPersistentEntityConfig) {

  import SlickClusteredPersistentEntityConfig._

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

  override lazy val components: SlickPersistenceComponents =
    new SlickPersistenceComponents with HikariCPComponents {
      override def actorSystem: ActorSystem = SlickClusteredPersistentEntitySpec.this.system
      override def executionContext: ExecutionContext = system.dispatcher
      override lazy val materializer: Materializer = ActorMaterializer.create(system)
      override lazy val configuration: Configuration = Configuration(system.settings.config)
      override def environment: Environment = SlickClusteredPersistentEntityConfig.environment
      override lazy val applicationLifecycle: ApplicationLifecycle = defaultApplicationLifecycle
      override def jsonSerializerRegistry: JsonSerializerRegistry = TestEntitySerializerRegistry
    }

  lazy val jdbcTestEntityReadSide: SlickTestEntityReadSide =
    new SlickTestEntityReadSide(
      components.db, components.profile
    )(components.executionContext)

  override protected def getAppendCount(id: String): Future[Long] =
    jdbcTestEntityReadSide.getAppendCount(id)

  override protected def readSideProcessor: () => ReadSideProcessor[Evt] = {
    () =>
      new SlickTestEntityReadSide.TestEntityReadSideProcessor(
        components.slickReadSide, components.db, components.profile
      )(components.executionContext)
  }
}
