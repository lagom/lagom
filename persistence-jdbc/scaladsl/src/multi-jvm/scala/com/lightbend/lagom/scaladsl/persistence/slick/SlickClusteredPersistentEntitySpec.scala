/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.slick

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.multinode.AbstractClusteredPersistentEntityConfig
import com.lightbend.lagom.scaladsl.persistence.multinode.AbstractClusteredPersistentEntitySpec
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry
import com.lightbend.lagom.scaladsl.persistence.multinode.AbstractClusteredPersistentEntitySpec.Ports
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.tools.Server
import play.api.Configuration
import play.api.Environment
import play.api.db.HikariCPComponents
import play.api.inject.ApplicationLifecycle
import play.api.inject.DefaultApplicationLifecycle

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object SlickClusteredPersistentEntityConfig extends AbstractClusteredPersistentEntityConfig {

  override def specPorts: Ports.SpecPorts = Ports.slickSpecPorts

  override def additionalCommonConfig: Config = ConfigFactory.parseString(
    s"""
      db.default.driver=org.h2.Driver
      db.default.url="jdbc:h2:tcp://localhost:${specPorts.database}/mem:JdbcClusteredPersistentEntitySpec"
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

  protected override def atStartup(): Unit = {
    runOn(node1) {
      h2 = Server.createTcpServer("-tcpPort", specPorts.database.toString, "-ifNotExists").start()
    }
    enterBarrier("h2-started")
    super.atStartup()
  }

  protected override def afterTermination(): Unit = {
    super.afterTermination()
    Await.ready(defaultApplicationLifecycle.stop(), shutdownTimeout)
    Option(h2).foreach(_.stop())
  }

  lazy val defaultApplicationLifecycle = new DefaultApplicationLifecycle

  override lazy val components: SlickPersistenceComponents =
    new SlickPersistenceComponents with HikariCPComponents {
      override def actorSystem: ActorSystem                 = SlickClusteredPersistentEntitySpec.this.system
      override def executionContext: ExecutionContext       = system.dispatcher
      override def coordinatedShutdown: CoordinatedShutdown = CoordinatedShutdown(actorSystem)

      override lazy val materializer: Materializer                 = SystemMaterializer(actorSystem).materializer
      override lazy val configuration: Configuration               = Configuration(system.settings.config)
      override def environment: Environment                        = SlickClusteredPersistentEntityConfig.environment
      override lazy val applicationLifecycle: ApplicationLifecycle = defaultApplicationLifecycle
      override def jsonSerializerRegistry: JsonSerializerRegistry  = TestEntitySerializerRegistry
    }

  lazy val jdbcTestEntityReadSide: SlickTestEntityReadSide =
    new SlickTestEntityReadSide(
      components.db,
      components.profile
    )(components.executionContext)

  protected override def getAppendCount(id: String): Future[Long] =
    jdbcTestEntityReadSide.getAppendCount(id)

  protected override def readSideProcessor: () => ReadSideProcessor[Evt] = { () =>
    new SlickTestEntityReadSide.TestEntityReadSideProcessor(
      components.slickReadSide,
      components.db,
      components.profile
    )(components.executionContext)
  }
}
