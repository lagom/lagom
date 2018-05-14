/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import akka.cluster.Cluster
import akka.pattern.AskTimeoutException
import com.lightbend.lagom.internal.persistence.testkit
import com.lightbend.lagom.persistence.ActorSystemSpec
import play.api.inject.{ ApplicationLifecycle, DefaultApplicationLifecycle }
import slick.jdbc.meta.MTable

import scala.concurrent.Await
import scala.concurrent.duration.{ FiniteDuration, _ }

class SlickOffsetStoreSpec extends ActorSystemSpec(testkit.clusterConfig.withFallback(testkit.loadTestConfig())) {

  import system.dispatcher

  private lazy val applicationLifecycle: ApplicationLifecycle = new DefaultApplicationLifecycle
  private lazy val slick = new SlickProvider(system)

  private lazy val offsetStore = new SlickOffsetStore(
    system,
    slick,
    TestOffsetStoreConfiguration()
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    // Trigger database to be loaded and registered to JNDI
    SlickDbTestProvider.buildAndBindSlickDb(system.name, applicationLifecycle)
  }

  override def afterAll(): Unit = {
    applicationLifecycle.stop()

    super.afterAll()
  }

  "SlickOffsetStoreSpec" when {
    "auto-creating tables is enabled" should {
      "allow prepare to be retried after a failure" in {
        val exception = Await.result(offsetStore.prepare("test_read_side", "TestTag").failed, 10.seconds)
        exception shouldBe a[AskTimeoutException]

        // Join ourselves - needed because we're using cluster singleton to create tables
        val cluster = Cluster(system)
        cluster.join(cluster.selfAddress)

        Await.result(offsetStore.prepare("test_read_side", "TestTag"), 20.seconds)

        val tables = Await.result(slick.db.run(MTable.getTables("test_read_side_offsets_tbl")), 5.seconds)
        tables should have length 1
      }
    }

  }

  private case class TestOffsetStoreConfiguration(
    tableName:                String         = "test_read_side_offsets_tbl",
    schemaName:               Option[String] = None,
    idColumnName:             String         = "test_read_side_id_col",
    tagColumnName:            String         = "test_tag_col",
    sequenceOffsetColumnName: String         = "test_sequence_offset_col",
    timeUuidOffsetColumnName: String         = "test_time_uuid_offset_col",
    minBackoff:               FiniteDuration = 1.second,
    maxBackoff:               FiniteDuration = 1.second,
    randomBackoffFactor:      Double         = 0,
    globalPrepareTimeout:     FiniteDuration = 5.seconds,
    role:                     Option[String] = None
  ) extends SlickOffsetStoreConfiguration

}
