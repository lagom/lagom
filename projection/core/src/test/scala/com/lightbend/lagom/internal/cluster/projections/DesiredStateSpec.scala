/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster.projections

import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerMetadata
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.State.WorkerName
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped
import com.lightbend.lagom.projection.Worker
import org.scalatest.Matchers
import org.scalatest.WordSpec

/**
 *
 */
class DesiredStateSpec extends WordSpec with Matchers {

  "DesiredState" should {

    "be build from a replicatedData" in {
      val p1w1      = "prj001-workers-1"
      val p1w2      = "prj001-workers-2"
      val p1w3      = "prj001-workers-3"
      val p2w1      = "prj002-workers-1"
      val meta001_1 = WorkerMetadata("stream", "prj001", p1w1)
      val meta001_2 = WorkerMetadata("stream", "prj001", p1w2)
      val meta001_3 = WorkerMetadata("stream", "prj001", p1w3)
      val meta002_1 = WorkerMetadata("stream", "prj002", p2w1)

      val nameIndex: Map[WorkerName, WorkerMetadata] = Map(
        p1w1 -> meta001_1,
        p1w2 -> meta001_2,
        p1w3 -> meta001_3,
        p2w1 -> meta002_1
      )

      val desiredStatus: Map[WorkerName, Status] = Map(
        p1w1 -> Stopped,
        p1w2 -> Started,
        p1w3 -> Stopped,
        p2w1 -> Started
      )
      val observedStatus: Map[WorkerName, Status] = Map(
        p1w1 -> Stopped,
        p1w2 -> Stopped,
        p1w3 -> Started,
        p2w1 -> Started
      )

      val desiredState = State.fromReplicatedData(nameIndex, desiredStatus, observedStatus)
      desiredState.projections.size should equal(2)
      desiredState.projections.flatMap(_.workers).size should equal(4)
      desiredState.projections.flatMap(_.workers).find(_.name == p1w3) shouldBe Some(
        Worker(p1w3, Stopped, Started)
      )

    }

  }
}
