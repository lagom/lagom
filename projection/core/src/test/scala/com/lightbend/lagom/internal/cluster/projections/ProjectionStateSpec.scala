/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster.projections

import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.State.ProjectionName
import com.lightbend.lagom.projection.State.WorkerKey
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped
import com.lightbend.lagom.projection.Worker
import org.scalatest.Matchers
import org.scalatest.WordSpec

/**
 *
 */
class ProjectionStateSpec extends WordSpec with Matchers {

  private val prj001   = "prj001"
  val p1w1             = prj001 + "-workers-1"
  val p1w2             = prj001 + "-workers-2"
  val p1w3             = prj001 + "-workers-3"
  val p2w1             = "prj002-workers-1"
  val coordinates001_1 = WorkerCoordinates(prj001, p1w1)
  val coordinates001_2 = WorkerCoordinates(prj001, p1w2)
  val coordinates001_3 = WorkerCoordinates(prj001, p1w3)
  val coordinates002_1 = WorkerCoordinates("prj002", p2w1)

  val nameIndex: Map[WorkerKey, WorkerCoordinates] = Map(
    coordinates001_1.asKey -> coordinates001_1,
    coordinates001_2.asKey -> coordinates001_2,
    coordinates001_3.asKey -> coordinates001_3,
    coordinates002_1.asKey -> coordinates002_1
  )

  val desiredWorkerStatus: Map[WorkerKey, Status] = Map(
    coordinates001_1.asKey -> Stopped,
    coordinates001_2.asKey -> Started,
    coordinates001_3.asKey -> Stopped,
    coordinates002_1.asKey -> Started
  )
  val observedStatus: Map[WorkerKey, Status] = Map(
    coordinates001_1.asKey -> Stopped,
    coordinates001_2.asKey -> Stopped,
    coordinates001_3.asKey -> Started,
    coordinates002_1.asKey -> Started
  )

  "ProjectionStateSpec" should {

    "be build from a replicatedData" in {
      val state = State.fromReplicatedData(nameIndex, desiredWorkerStatus, Map.empty, observedStatus, Started, Stopped)
      state.projections.size should equal(2)
      state.projections.flatMap(_.workers).size should equal(4)
      state.projections.flatMap(_.workers).find(_.key == coordinates001_3.asKey) shouldBe Some(
        Worker(p1w3, coordinates001_3.asKey, Stopped, Started)
      )
    }

    "find projection by name" in {
      val state = State.fromReplicatedData(nameIndex, desiredWorkerStatus, Map.empty, observedStatus, Started, Stopped)
      state.findProjection(prj001) should not be None
    }

    "find worker by key" in {
      val state       = State.fromReplicatedData(nameIndex, desiredWorkerStatus, Map.empty, observedStatus, Started, Stopped)
      val maybeWorker = state.findWorker("prj001-prj001-workers-3")
      maybeWorker shouldBe Some(
        Worker(p1w3, coordinates001_3.asKey, Stopped, Started)
      )
    }

    "build from projection values when workers in nameIndex don't have request" in {
      val newProjectionName = "new-projection"
      val newWorkerName     = "new-worker-001"
      val newCoordinates    = WorkerCoordinates(newProjectionName, newWorkerName)
      val richIndex = nameIndex ++ Map(
        newCoordinates.asKey -> newCoordinates
      )

      val desiredProjectionStatus: Map[ProjectionName, Status] = Map(
        newProjectionName -> Stopped
      )

      val defaultRequested = Started
      val defaultObserved  = Started

      val state = State.fromReplicatedData(
        richIndex,
        desiredWorkerStatus,
        desiredProjectionStatus,
        observedStatus,
        defaultRequested,
        defaultObserved
      )
      val maybeWorker = state.findWorker(newCoordinates.asKey)
      maybeWorker shouldBe Some(
        Worker(newWorkerName, newCoordinates.asKey, Stopped, Started)
      )
    }

    "build from default values when workers in nameIndex don't have request or observed values" in {
      val newProjectionName = "new-projection"
      val newWorkerName     = "new-worker-001"
      val newCoordinates    = WorkerCoordinates(newProjectionName, newWorkerName)
      val richIndex = nameIndex ++ Map(
        newCoordinates.asKey -> newCoordinates
      )

      val defaultRequested = Stopped
      val defaultObserved  = Started

      val state = State.fromReplicatedData(
        richIndex,
        desiredWorkerStatus,
        Map.empty,
        observedStatus,
        defaultRequested,
        defaultObserved
      )
      val maybeWorker = state.findWorker(newCoordinates.asKey)
      maybeWorker shouldBe Some(
        Worker(newWorkerName, newCoordinates.asKey, defaultRequested, defaultObserved)
      )
    }

  }
}
