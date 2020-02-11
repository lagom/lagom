/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletionStage

import com.google.inject.Guice
import com.lightbend.lagom.internal.javadsl.persistence.cassandra.CassandraPersistentEntityRegistry
import com.lightbend.lagom.internal.javadsl.persistence.cassandra.CassandraReadSideImpl
import com.lightbend.lagom.internal.javadsl.persistence.cassandra.JavadslCassandraOffsetStore
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideSettings
import com.lightbend.lagom.javadsl.persistence.Offset.TimeBasedUUID
import com.lightbend.lagom.javadsl.persistence._
import com.typesafe.config.ConfigFactory
import play.api.inject.guice.GuiceInjectorBuilder
import scala.concurrent.duration._

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
    extends CassandraPersistenceSpec(CassandraReadSideSpec.defaultConfig)
    with AbstractReadSideSpec {
  import system.dispatcher

  private lazy val injector                            = new GuiceInjectorBuilder().build()
  protected override lazy val persistentEntityRegistry = new CassandraPersistentEntityRegistry(system, injector)

  private lazy val testSession: CassandraSession                      = new CassandraSession(system)
  private lazy val testCasReadSideSettings: CassandraReadSideSettings = new CassandraReadSideSettings(system)
  private lazy val offsetStore =
    new JavadslCassandraOffsetStore(system, testSession, testCasReadSideSettings, ReadSideConfig())
  private lazy val cassandraReadSide = new CassandraReadSideImpl(system, testSession, offsetStore, null, injector)

  override def processorFactory(): ReadSideProcessor[TestEntity.Evt] =
    new TestEntityReadSide.TestEntityReadSideProcessor(cassandraReadSide, testSession)

  private lazy val readSide = new TestEntityReadSide(testSession)

  override def getAppendCount(id: String): CompletionStage[java.lang.Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }

}

class CassandraReadSideAutoCreateSpec extends CassandraPersistenceSpec(CassandraReadSideSpec.noAutoCreateConfig) {
  import system.dispatcher

  private lazy val testSession: CassandraSession                      = new CassandraSession(system)
  private lazy val testCasReadSideSettings: CassandraReadSideSettings = new CassandraReadSideSettings(system)
  private lazy val offsetStore =
    new JavadslCassandraOffsetStore(system, testSession, testCasReadSideSettings, ReadSideConfig())

  "A Cassandra Read-Side" must {
    "not send ClusterStartupTask message, so startupTask must return None" +
      "when 'lagom.persistence.read-side.cassandra.tables-autocreate' flag is 'false'" in {
      offsetStore.startupTask shouldBe None
    }
  }
}
