/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ CassandraPersistentEntityRegistry, CassandraReadSideImpl, ScaladslCassandraOffsetStore }
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration._

object CassandraReadSideSpec {

  val config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    """)

}

class CassandraReadSideSpec extends CassandraPersistenceSpec(CassandraReadSideSpec.config, TestEntitySerializerRegistry) with AbstractReadSideSpec {
  import system.dispatcher

  override protected lazy val persistentEntityRegistry = new CassandraPersistentEntityRegistry(system)

  private lazy val testSession: CassandraSession = new CassandraSession(system)
  private lazy val offsetStore = new ScaladslCassandraOffsetStore(system, testSession, ReadSideConfig())
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
