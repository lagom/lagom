/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster.projections

import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.ProjectionName
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.Projection
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
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
  private val prj002   = "prj002"
  val p1w1             = prj001 + "-workers-1"
  val p1w2             = prj001 + "-workers-2"
  val p1w3             = prj001 + "-workers-3"
  val p2w1             = s"$prj002-workers-1"
  val coordinates001_1 = WorkerCoordinates(prj001, p1w1)
  val coordinates001_2 = WorkerCoordinates(prj001, p1w2)
  val coordinates001_3 = WorkerCoordinates(prj001, p1w3)
  val coordinates002_1 = WorkerCoordinates(prj002, p2w1)

  val nameIndex: Map[ProjectionName, Set[WorkerCoordinates]] = Map(
    prj001 -> Set(coordinates001_1, coordinates001_2, coordinates001_3),
    prj002 -> Set(coordinates002_1)
  )

  val requestedStatus: Map[WorkerCoordinates, Status] = Map(
    coordinates001_1 -> Stopped,
    coordinates001_2 -> Started,
    coordinates001_3 -> Stopped,
    coordinates002_1 -> Started
  )
  val observedStatus: Map[WorkerCoordinates, Status] = Map(
    coordinates001_1 -> Stopped,
    coordinates001_2 -> Stopped,
    coordinates001_3 -> Started,
    coordinates002_1 -> Started
  )

  def findProjection(state: State)(projectionName: String): Option[Projection] =
    state.projections.find(_.name == projectionName)

  def findWorker(state: State)(workerKey: String): Option[Worker] =
    state.projections.flatMap(_.workers).find(_.key == workerKey)

  "ProjectionStateSpec" should {
    "be build from a replicatedData" in {
      val state = State.fromReplicatedData(nameIndex, requestedStatus, observedStatus, Started, Stopped)
      state.projections.size should equal(2)
      state.projections.flatMap(_.workers).size should equal(4)
      state.projections.flatMap(_.workers).find(_.key == coordinates001_3.asKey) shouldBe Some(
        Worker(p1w3, coordinates001_3.asKey, Stopped, Started)
      )
    }

    "find projection by name" in {
      val state = State.fromReplicatedData(nameIndex, requestedStatus, observedStatus, Started, Stopped)
      findProjection(state)(prj001) should not be None
    }

    "find worker by key" in {
      val state       = State.fromReplicatedData(nameIndex, requestedStatus, observedStatus, Started, Stopped)
      val maybeWorker = findWorker(state)("prj001-prj001-workers-3")
      maybeWorker shouldBe Some(
        Worker(p1w3, coordinates001_3.asKey, Stopped, Started)
      )
    }

    "build from default values when workers in nameIndex don't have request or observed values" in {
      val newProjectionName = "new-projection"
      val newWorkerName     = "new-worker-001"
      val newCoordinates    = WorkerCoordinates(newProjectionName, newWorkerName)
      val richIndex = nameIndex ++ Map(
        newProjectionName -> Set(newCoordinates)
      )

      val defaultRequested = Stopped
      val defaultObserved  = Started

      val state =
        State.fromReplicatedData(richIndex, requestedStatus, observedStatus, defaultRequested, defaultObserved)
      val maybeWorker = findWorker(state)(newCoordinates.asKey)
      maybeWorker shouldBe Some(
        Worker(newWorkerName, newCoordinates.asKey, defaultRequested, defaultObserved)
      )
    }
  }
}
