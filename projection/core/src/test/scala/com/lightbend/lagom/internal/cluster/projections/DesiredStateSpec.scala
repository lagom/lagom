/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster.projections

import com.lightbend.lagom.internal.projection.ProjectionRegistry.Started
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.DesiredState
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerMetadata
import org.scalatest.Matchers
import org.scalatest.WordSpec

/**
 *
 */
class DesiredStateSpec extends WordSpec with Matchers {

  "DesiredState" should {

    "be build from a replicatedData" in {
      val meta001_1 = WorkerMetadata("stream", "prj001", "prj001-workers-1")
      val meta001_2 = WorkerMetadata("stream", "prj001", "prj001-workers-2")
      val meta001_3 = WorkerMetadata("stream", "prj001", "prj001-workers-3")
      val meta002_1 = WorkerMetadata("stream", "prj002", "prj001-workers-1")

      val ddata = Map(
        (meta001_1, Started),
        (meta001_2, Started),
        (meta001_3, Started),
        (meta002_1, Started)
      )

      val desiredState = DesiredState.fromReplicatedData(ddata)
      desiredState.projections.size should equal(2)
      desiredState.projections.flatMap(_.workers).size should equal(4)

    }

  }
}
