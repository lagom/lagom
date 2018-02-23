/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import akka.actor.setup.ActorSystemSetup
import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.cluster.Cluster
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.{ SlickDbTestProvider, SlickOffsetStore, SlickProvider }
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.OffsetTableConfiguration
import com.lightbend.lagom.internal.scaladsl.persistence.slick.SlickReadSideImpl
import com.lightbend.lagom.persistence.{ ActorSystemSpec, PersistenceSpec }
import com.lightbend.lagom.scaladsl.persistence.jdbc.testkit.TestUtil
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.inject.{ ApplicationLifecycle, DefaultApplicationLifecycle }
import play.api.{ Configuration, Environment }

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }

abstract class SlickPersistenceSpec private (_system: ActorSystem)(implicit ec: ExecutionContext)
  extends ActorSystemSpec(_system) {

  def this(testName: String, config: Config, registry: JsonSerializerRegistry)(implicit ec: ExecutionContext) =
    this(ActorSystem(testName, ActorSystemSetup(
      BootstrapSetup(
        config.withFallback(TestUtil.clusterConfig()).withFallback(Configuration.load(Environment.simple()).underlying)
      ),
      JsonSerializerRegistry.serializationSetupFor(registry)
    )))

  def this(config: Config, registry: JsonSerializerRegistry)(implicit ec: ExecutionContext) =
    this(PersistenceSpec.getCallerName(getClass), config, registry)

  def this(registry: JsonSerializerRegistry)(implicit ec: ExecutionContext) =
    this(ConfigFactory.empty(), registry)

  protected lazy val slick = new SlickProvider(system)
  protected lazy val slickReadSide: SlickReadSide = new SlickReadSideImpl(
    slick,
    new SlickOffsetStore(
      system,
      slick,
      new OffsetTableConfiguration(system.settings.config, ReadSideConfig())
    )
  )

  private lazy val applicationLifecycle: ApplicationLifecycle = new DefaultApplicationLifecycle

  override def beforeAll(): Unit = {
    super.beforeAll()

    // Join ourselves - needed because we're using cluster singleton to create tables
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)

    // Trigger database to be loaded and registered to JNDI
    SlickDbTestProvider.buildAndBindSlickDb(system.name, applicationLifecycle)

    // Trigger tables to be created
    Await.ready(slick.ensureTablesCreated(), 20.seconds)

    TestUtil.awaitPersistenceInit(system)
  }

  override def afterAll(): Unit = {
    applicationLifecycle.stop()
    super.afterAll()
  }
}
