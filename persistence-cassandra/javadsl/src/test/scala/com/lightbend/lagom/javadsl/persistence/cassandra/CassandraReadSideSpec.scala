/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.concurrent.CompletionStage

import akka.Done
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

import scala.compat.java8.FutureConverters
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

object CassandraReadSideSpec {

  val defaultConfig      = ConfigFactory.parseString("akka.loglevel = INFO")
  val noAutoCreateConfig = ConfigFactory.parseString("lagom.persistence.read-side.cassandra.tables-autocreate = false")
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

  import FutureConverters._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // eagerly prepare database to reduce flakiness in travis.
    val timeout: FiniteDuration = 15.seconds
    implicit val akkaTo = akka.util.Timeout.durationToTimeout(timeout)
    val f = for {
      _ <- offsetStore.startupTask.map(_.askExecute()).getOrElse(Future.successful(Done))
      _ <- processorFactory().buildHandler().globalPrepare().toScala
    } yield Done
    Await.result(f, timeout)
  }

  override def afterAll(): Unit = {
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
