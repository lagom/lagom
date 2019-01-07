/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jdbc

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.lightbend.lagom.internal.javadsl.persistence.jdbc._
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.SlickDbTestProvider
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.persistence.{ ActorSystemSpec, PersistenceSpec }
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.inject.{ ApplicationLifecycle, DefaultApplicationLifecycle }
import play.api.{ Configuration, Environment }

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class JdbcPersistenceSpec(_system: ActorSystem) extends ActorSystemSpec(_system) {

  def this(testName: String, config: Config) =
    this(ActorSystem(testName, config.withFallback(Configuration.load(Environment.simple()).underlying)))

  def this(config: Config) = this(PersistenceSpec.getCallerName(getClass), config)

  def this() = this(ConfigFactory.empty())

  import system.dispatcher

  protected lazy val slick = new SlickProvider(system)

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

    awaitPersistenceInit(system)
  }

  override def afterAll(): Unit = {
    applicationLifecycle.stop()
    super.afterAll()
  }

}
