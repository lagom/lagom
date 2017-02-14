/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.actor.setup.ActorSystemSetup
import akka.cluster.Cluster
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.{ SlickOffsetStore, SlickProvider }
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.{ JdbcReadSideImpl, JdbcSessionImpl, OffsetTableConfiguration }
import com.lightbend.lagom.persistence.{ ActorSystemSpec, PersistenceSpec }
import com.lightbend.lagom.scaladsl.persistence.jdbc.testkit.TestUtil
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.db.{ Database, Databases }
import play.api.{ Configuration, Environment }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

abstract class JdbcPersistenceSpec private (_system: ActorSystem) extends ActorSystemSpec(_system) {

  def this(testName: String, config: Config, registry: JsonSerializerRegistry) =
    this(ActorSystem(testName, ActorSystemSetup(
      BootstrapSetup(
        config.withFallback(TestUtil.clusterConfig()).withFallback(Configuration.load(Environment.simple()).underlying)
      ),
      JsonSerializerRegistry.serializationSetupFor(registry)
    )))

  def this(config: Config, registry: JsonSerializerRegistry) = this(PersistenceSpec.getCallerName(getClass), config, registry)

  def this(registry: JsonSerializerRegistry) = this(ConfigFactory.empty(), registry)

  // late initialization of database
  private var _database: Option[Database] = None
  protected def database = _database match {
    case Some(db) => db
    case None =>
      val dbName = s"${system.name}_${Random.alphanumeric.take(8).mkString}"

      val db = Databases.inMemory(dbName, config = Map("jndiName" -> "DefaultDS"))
      _database = Some(db)
      db
  }

  import system.dispatcher
  protected lazy val slick = new SlickProvider(system, null)
  protected lazy val session: JdbcSession = new JdbcSessionImpl(slick)
  protected lazy val jdbcReadSide: JdbcReadSide = new JdbcReadSideImpl(
    slick,
    new SlickOffsetStore(
      system,
      slick,
      new OffsetTableConfiguration(Configuration(system.settings.config), ReadSideConfig())
    )
  )

  override def beforeAll(): Unit = {
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

  override def afterAll(): Unit = {
    _database.foreach(_.shutdown())
    super.afterAll()
  }

}
