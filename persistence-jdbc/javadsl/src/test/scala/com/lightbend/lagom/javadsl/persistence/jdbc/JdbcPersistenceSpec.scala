/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jdbc

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.lightbend.lagom.internal.javadsl.persistence.jdbc._
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.SlickDbTestProvider
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.persistence.PersistenceSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.Environment

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class JdbcPersistenceSpec private (_system: ActorSystem) extends ActorSystemSpec(_system) {
  def this(testName: String, config: Config) = {
    this(ActorSystem(testName, config.withFallback(Configuration.load(Environment.simple()).underlying)))
  }

  def this(config: Config) = this(PersistenceSpec.testNameFromCallStack(classOf[JdbcPersistenceSpec]), config)

  def this() = this(ConfigFactory.empty())

  import system.dispatcher

  protected lazy val slick = new SlickProvider(system, coordinatedShutdown)

  protected lazy val offsetStore =
    new JavadslJdbcOffsetStore(
      slick,
      system,
      new OffsetTableConfiguration(
        system.settings.config,
        ReadSideConfig()
      ),
      ReadSideConfig()
    )
  protected lazy val jdbcReadSide: JdbcReadSide = new JdbcReadSideImpl(slick, offsetStore)

  override def beforeAll(): Unit = {
    super.beforeAll()

    // Join ourselves - needed because we're using cluster singleton to create tables
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)

    // Trigger database to be loaded and registered to JNDI
    SlickDbTestProvider.buildAndBindSlickDb(system.name, coordinatedShutdown)

    // Trigger tables to be created
    Await.ready(slick.ensureTablesCreated(), 20.seconds)

    awaitPersistenceInit(system)
  }
}
