/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc
import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.lightbend.lagom.internal.persistence.jdbc._
import com.lightbend.lagom.javadsl.persistence.jdbc.testkit.TestUtil
import com.lightbend.lagom.javadsl.persistence.{ ActorSystemSpec, PersistenceSpec }
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.{ Configuration, Environment }
import play.api.db.Databases

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

abstract class JdbcPersistenceSpec(_system: ActorSystem) extends ActorSystemSpec(_system) {

  def this(testName: String, config: Config) =
    this(ActorSystem(testName, config.withFallback(TestUtil.clusterConfig()).withFallback(Configuration.load(Environment.simple()).underlying)))

  def this(config: Config) = this(PersistenceSpec.getCallerName(getClass), config)

  def this() = this(ConfigFactory.empty())

  import system.dispatcher

  protected lazy val database = {
    val dbName = s"${system.name}_${Random.alphanumeric.take(8).mkString}"

    Databases.inMemory(dbName, config = Map("jndiName" -> "DefaultDS"))
  }

  protected lazy val slick = new SlickProvider(system, null)
  protected lazy val session: JdbcSession = new JdbcSessionImpl(slick)
  protected lazy val jdbcReadSide: JdbcReadSide = new JdbcReadSideImpl(
    slick,
    new JdbcOffsetStore(slick, new OffsetTableConfiguration(Configuration(system.settings.config)))
  )

  override def beforeAll {
    super.beforeAll()

    // Join ourselves - needed because we're using cluster singleton to create tables
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)

    // Trigger database to be loaded and registered to JNDI
    database.dataSource

    // Trigger tables to be created
    Await.ready(slick.ensureTablesCreated(), 20.seconds)

    TestUtil.awaitPersistenceInit(system)
  }

  override def afterAll {
    super.afterAll()
    Option(database).foreach(_.shutdown())
  }

}
