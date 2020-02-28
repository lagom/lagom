/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import akka.persistence.query.TimeBasedUUID
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideSettings
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.CassandraPersistentEntityRegistry
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.CassandraReadSideImpl
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.ScaladslCassandraOffsetStore
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._

object CassandraReadSideSpec {
  def firstTimeBucket: String = {
    val today                                = LocalDateTime.now(ZoneOffset.UTC)
    val firstBucketFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm")
    today.minusHours(3).format(firstBucketFormat)
  }

  val readSideConfig = ConfigFactory.parseString(s"""
    # speed up read-side queries
    cassandra-query-journal {
      first-time-bucket = "$firstTimeBucket"
      refresh-interval = 1s
      events-by-tag.eventual-consistency-delay = 1s
    }
    """)

  val defaultConfig =
    ConfigFactory
      .parseString("akka.loglevel = INFO")
      .withFallback(readSideConfig)

  val noAutoCreateConfig =
    ConfigFactory
      .parseString("lagom.persistence.read-side.cassandra.tables-autocreate = false")
      .withFallback(defaultConfig)
}

class CassandraReadSideSpec
    extends CassandraPersistenceSpec(CassandraReadSideSpec.defaultConfig, TestEntitySerializerRegistry)
    with AbstractReadSideSpec {
  import system.dispatcher

  protected override lazy val persistentEntityRegistry = new CassandraPersistentEntityRegistry(system)

  private lazy val testCasReadSideSettings: CassandraReadSideSettings = new CassandraReadSideSettings(system)
  private lazy val testSession: CassandraSession                      = new CassandraSession(system)
  private lazy val offsetStore =
    new ScaladslCassandraOffsetStore(system, testSession, testCasReadSideSettings, ReadSideConfig())
  private lazy val cassandraReadSide = new CassandraReadSideImpl(system, testSession, offsetStore)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new TestEntityReadSide.TestEntityReadSideProcessor(system, cassandraReadSide, testSession)

  private lazy val readSide = new TestEntityReadSide(system, testSession)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    super.afterAll()
  }
}

class CassandraReadSideAutoCreateSpec
    extends CassandraPersistenceSpec(CassandraReadSideSpec.noAutoCreateConfig, TestEntitySerializerRegistry) {
  import system.dispatcher

  private lazy val testSession: CassandraSession                      = new CassandraSession(system)
  private lazy val testCasReadSideSettings: CassandraReadSideSettings = new CassandraReadSideSettings(system)
  private lazy val offsetStore =
    new ScaladslCassandraOffsetStore(system, testSession, testCasReadSideSettings, ReadSideConfig())

  "A Cassandra Read-Side" must {
    "not send ClusterStartupTask message, so startupTask must return None" +
      "when 'lagom.persistence.read-side.cassandra.tables-autocreate' flag is 'false'" in {
      offsetStore.startupTask shouldBe None
    }
  }
}
