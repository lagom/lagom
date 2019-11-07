/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.jdbc

import akka.cluster.Cluster
import akka.pattern.AskTimeoutException
import com.lightbend.lagom.persistence.ActorSystemSpec
import play.api.Configuration
import play.api.Environment
import slick.jdbc.meta.MTable

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class SlickOffsetStoreSpec extends ActorSystemSpec(Configuration.load(Environment.simple()).underlying) {
  import system.dispatcher

  private lazy val slick = new SlickProvider(system, coordinatedShutdown)

  private lazy val offsetStore = new SlickOffsetStore(
    system,
    slick,
    TestOffsetStoreConfiguration()
  )

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    // Trigger database to be loaded and registered to JNDI
    SlickDbTestProvider.buildAndBindSlickDb(system.name, coordinatedShutdown)
  }

  "SlickOffsetStoreSpec" when {
    "auto-creating tables is enabled" should {
      // Regression test for https://github.com/lagom/lagom/issues/1336
      "allow prepare to be retried after a failure" in {
        val exception = Await.result(offsetStore.prepare("test_read_side", "TestTag").failed, 10.seconds)
        exception shouldBe a[AskTimeoutException]

        // Join ourselves - needed because we're using cluster singleton to create tables
        val cluster = Cluster(system)
        cluster.join(cluster.selfAddress)

        Await.result(offsetStore.prepare("test_read_side", "TestTag"), 20.seconds)

        val tables = Await.result(slick.db.run(MTable.getTables("test_read_side_offsets_tbl")), 5.seconds)
        (tables should have).length(1)
      }

      /*
       * TODO this should include more test cases, including "happy path" and idempotency cases:
       * Currently only one instance of SlickOffsetStore can be created per actor system, because of the cluster startup
       * task it creates, which must have a unique name. There is no way to cleanly shut down the offset store,
       * including the cluster startup task, and there is no way to namespace the cluster startup task to allow for more
       * than one in the lifetime of the actor system.
       *
       * Note that this also implies that there is no way to create multiple SlickOffsetStore instances for different
       * databases/schemas. This would be a good improvement. See https://github.com/lagom/lagom/issues/1353
       *
       * Some suggested tests are pending below:
       */
      "creates the read-side offset table when preparing" in pending
      "allows prepare to be called multiple times" in pending
      "uses configured column names" in pending
      "returns an offset DAO with the last stored offset" in pending
    }

    "auto-creating tables is disabled" in pending
  }

  private case class TestOffsetStoreConfiguration(
      tableName: String = "test_read_side_offsets_tbl",
      schemaName: Option[String] = None,
      idColumnName: String = "test_read_side_id_col",
      tagColumnName: String = "test_tag_col",
      sequenceOffsetColumnName: String = "test_sequence_offset_col",
      timeUuidOffsetColumnName: String = "test_time_uuid_offset_col",
      minBackoff: FiniteDuration = 1.second,
      maxBackoff: FiniteDuration = 1.second,
      randomBackoffFactor: Double = 0,
      globalPrepareTimeout: FiniteDuration = 5.seconds,
      role: Option[String] = None
  ) extends SlickOffsetStoreConfiguration
}
