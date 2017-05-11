/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import scala.concurrent.Future
import scala.concurrent.duration._
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.CassandraProvider
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{CassandraPersistentEntityRegistry, CassandraReadSideImpl, ScaladslCassandraOffsetStore}
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._
import com.typesafe.config.ConfigFactory

object CassandraReadSideSpec {

  val defaultConfig = ConfigFactory.parseString("akka.loglevel = INFO")
  val noAutoCreateConfig = ConfigFactory.parseString("lagom.persistence.read-side.cassandra.tables-autocreate = false")
}

class CassandraReadSideSpec extends CassandraPersistenceSpec(CassandraReadSideSpec.defaultConfig, TestEntitySerializerRegistry) with AbstractReadSideSpec {
  import system.dispatcher

  override protected lazy val persistentEntityRegistry = new CassandraPersistentEntityRegistry(system)

  private lazy val testCasConfigProvider: CassandraProvider = new CassandraProvider(system)
  private lazy val testSession: CassandraSession = new CassandraSession(system)
  private lazy val offsetStore = new ScaladslCassandraOffsetStore(system, testSession, testCasConfigProvider, ReadSideConfig())
  private lazy val cassandraReadSide = new CassandraReadSideImpl(system, testSession, offsetStore)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new TestEntityReadSide.TestEntityReadSideProcessor(system, cassandraReadSide, testSession)

  private lazy val readSide = new TestEntityReadSide(system, testSession)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }
}

class CassandraReadSideAutoCreateSpec extends CassandraPersistenceSpec(CassandraReadSideSpec.noAutoCreateConfig, TestEntitySerializerRegistry) {
  import system.dispatcher

  private lazy val testSession: CassandraSession = new CassandraSession(system)
  private lazy val testCasConfigProvider: CassandraProvider = new CassandraProvider(system)
  private lazy val offsetStore = new ScaladslCassandraOffsetStore(system, testSession, testCasConfigProvider, ReadSideConfig())

  "ReadSide" must {
    "not auto create offset store table when 'lagom.persistence.read-side.cassandra.tables-autocreate' flag is 'false'" in {
      offsetStore.startupTask.isCompleted shouldBe true
    }
  }
}
