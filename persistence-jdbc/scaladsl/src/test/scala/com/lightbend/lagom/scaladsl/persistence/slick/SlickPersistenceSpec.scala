/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import akka.actor.setup.ActorSystemSetup
import akka.actor.ActorSystem
import akka.actor.BootstrapSetup
import akka.cluster.Cluster
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.SlickDbTestProvider
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetStore
import com.lightbend.lagom.internal.persistence.jdbc.SlickProvider
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.OffsetTableConfiguration
import com.lightbend.lagom.internal.scaladsl.persistence.slick.SlickReadSideImpl
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.persistence.PersistenceSpec
import com.lightbend.lagom.scaladsl.persistence.jdbc.testkit.TestUtil
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import play.api.inject.ApplicationLifecycle
import play.api.inject.DefaultApplicationLifecycle
import play.api.Configuration
import play.api.Environment

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext

abstract class SlickPersistenceSpec private (_system: ActorSystem) extends ActorSystemSpec(_system) {

  def this(testName: String, config: Config, registry: JsonSerializerRegistry) =
    this(
      ActorSystem(
        testName,
        ActorSystemSetup(
          BootstrapSetup(
            config
              .withFallback(TestUtil.clusterConfig())
              .withFallback(Configuration.load(Environment.simple()).underlying)
          ),
          JsonSerializerRegistry.serializationSetupFor(registry)
        )
      )
    )

  def this(config: Config, registry: JsonSerializerRegistry) =
    this(PersistenceSpec.getCallerName(getClass), config, registry)

  def this(registry: JsonSerializerRegistry) =
    this(ConfigFactory.empty(), registry)

  import system.dispatcher

  protected lazy val slick = new SlickProvider(system)
  protected lazy val slickReadSide: SlickReadSide = {
    val offsetStore =
      new SlickOffsetStore(
        system,
        slick,
        new OffsetTableConfiguration(system.settings.config, ReadSideConfig())
      )
    new SlickReadSideImpl(slick, offsetStore)
  }

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
