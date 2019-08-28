/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.Done
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Terminated
import akka.remote.testconductor.RoleName
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.cluster.ClusteredMultiNodeUtils
import com.lightbend.lagom.internal.cluster.MultiNodeExpect
import com.lightbend.lagom.internal.projection.FakeProjectionActor.FakeStarting
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.Projection
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import scala.concurrent.Await

class ProjectionRegistrySpecMultiJvmNode1 extends ProjectionRegistrySpec
class ProjectionRegistrySpecMultiJvmNode2 extends ProjectionRegistrySpec
class ProjectionRegistrySpecMultiJvmNode3 extends ProjectionRegistrySpec

class ProjectionRegistrySpec extends ClusteredMultiNodeUtils(numOfNodes = 3) with Eventually with ScalaFutures {
  implicit val exCtx             = system.dispatcher
  private val pc                 = PatienceConfig(timeout = Span(20, Seconds), interval = Span(2, Seconds))
  private val projectionRegistry = new ProjectionRegistry(system)
  // distributed expect ops need a generous timeout
  private val multiExpectTimeout = 30.seconds

  "A ProjectionRegistry" must {

    "register a projection with a single worker" in {
      val projectionName = "test-canary"
      val tagNamePrefix  = "streamName"
      val tagName001     = s"${tagNamePrefix}001"

      registerProjection(projectionName, Set(tagName001))

      expectWorkerStatus(projectionName, tagName001, Started)
    }

    "register a projection with multiple workers" in {
      val projectionName = "test-canary-many-workers"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")

      registerProjection(projectionName, tagNames.toSet)
      expectProjectionStatus(projectionName, 5, Started)
    }

    "request the pause of a projection worker" in {
      enterBarrier("request-pause-worker-test")
      val projectionName = "test-pause-worker"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head
      val coordinates001 = WorkerCoordinates(projectionName, tagNames.head)

      registerProjection(projectionName, tagNames.toSet)

      // await until seen as ready
      expectWorkerStatus(projectionName, tagName001, Started)

      enterBarrier("request-pause-worker-test-all-nodes-ready")
      runOn(RoleName("node2")) {
        projectionRegistry.stopWorker(coordinates001)
      }
      expectWorkerStatus(projectionName, tagName001, Stopped)
    }

    "request the pause of a complete projection" in {
      enterBarrier("request-pause-projection-test")
      val projectionName = "test-pause-projection"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")

      registerProjection(projectionName, tagNames.toSet)

      // await until seen as ready
      expectProjectionStatus(projectionName, 5, Started)

      // Don't try to `stopWorkers` until we've seen `desired` propagate completely
      enterBarrier("request-pause-projection-test-all-nodes-ready")
      runOn(RoleName("node2")) {
        projectionRegistry.stopAllWorkers(projectionName)
      }
      expectProjectionStatus(projectionName, 5, Stopped)
    }

    "tell a projection worker to stop when requested" in {
      enterBarrier("do-pause-worker-test")
      val projectionName = "do-pause-worker"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head
      val coordinates001 = WorkerCoordinates(projectionName, tagNames.head)

      val testProbe = registerProjection(projectionName, tagNames.toSet)
      testProbe.ignoreMsg {
        case Terminated(_) => false
        case _             => true
      }

      expectWorkerStatus(projectionName, tagName001, Started)

      enterBarrier("do-pause-worker-test-all-nodes-ready")
      runOn(RoleName("node2")) {
        projectionRegistry.stopWorker(coordinates001)
      }
      expectWorkerStatus(projectionName, tagName001, Stopped)
    }

    "tell a projection worker to stop and then start when requested" in {
      enterBarrier("do-pause-and-resume-worker-test")
      val projectionName = "do-pause-and-resume-worker"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head
      val coordinates001 = WorkerCoordinates(projectionName, tagNames.head)

      // build a projection with a single worker bound to run on `node3`
      val testProbe = registerProjection(projectionName, tagNames.toSet)
      testProbe.ignoreMsg {
        case _ => true
      }

      // await until seen as ready
      expectWorkerStatus(projectionName, tagName001, Started)

      enterBarrier("do-pause-and-resume-worker-test-all-nodes-ready-001")
      runOn(RoleName("node2")) {
        projectionRegistry.stopWorker(coordinates001)
      }

      expectWorkerStatus(projectionName, tagName001, Stopped)
      enterBarrier("do-pause-and-resume-worker-test-all-nodes-ready-002")

      // once the worker is stopped we no longer want to ignore messages in the probe.
      testProbe.ignoreNoMsg()
      runOn(RoleName("node3")) {
        projectionRegistry.startWorker(coordinates001)
      }
      expectMsgFromWorker(
        FakeStarting(tagName001),
        "do-pause-and-resume-worker-test-expect-starting",
        testProbe,
        multiExpectTimeout
      )
    }

  }

  private def expectMsgFromWorker[T](t: T, expectationKey: String, testProbe: TestProbe, max: FiniteDuration): Unit = {
    val multiNodeExpect = new MultiNodeExpect(testProbe)
    val expectStarting  = multiNodeExpect.expectMsg(t, expectationKey, max)
    Await.result(expectStarting, multiExpectTimeout) shouldBe Done
  }

  private def expectWorkerStatus(projectionName: String, tagName: String, expectedStatus: Status) = {
    eventually(Timeout(pc.timeout), Interval(pc.interval)) {
      whenReady(projectionRegistry.getState()) { x =>
        val maybeWorker = x.findWorker(WorkerCoordinates(projectionName, tagName).asKey)
        maybeWorker.map(_.observedStatus) should be(Some(expectedStatus))
        maybeWorker.map(_.requestedStatus) should be(Some(expectedStatus))
      }
    }
  }

  private def expectProjectionStatus(projectionName: String, expectedWorkerCount: Int, expectedStatus: Status) = {
    eventually(Timeout(pc.timeout), Interval(pc.interval)) {
      whenReady(projectionRegistry.getState()) { state =>
        val projection: Projection = state.findProjection(projectionName).get
        projection.workers.size shouldBe expectedWorkerCount
        projection.workers.forall(_.requestedStatus == expectedStatus) should be(true)
        projection.workers.forall(_.observedStatus == expectedStatus) should be(true)
      }
    }

  }

  private def registerProjection(
      projectionName: String,
      tagNames: Set[String],
      runInRole: Option[String] = None
  ): TestProbe = {
    val testProbe = TestProbe()

    val workerProps = (workerCoordinates: WorkerCoordinates) =>
      FakeProjectionActor.props(
        workerCoordinates.tagName,
        testProbe
      )

    projectionRegistry.registerProjection(projectionName, tagNames, workerProps, runInRole)

    tagNames.foreach { tagName =>
      projectionRegistry.startWorker(WorkerCoordinates(projectionName, tagName))
    }

    testProbe
  }
}

object FakeProjectionActor {

  case class FakeStarting(tagName: String)

  def props(tagName: String, testProbe: TestProbe): Props =
    Props(new FakeProjectionActor(tagName, testProbe))
}

// its state should be an actual copy of the desired state in the projection registry
class FakeProjectionActor(tagName: String, testProbe: TestProbe) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    super.preStart()
    testProbe.ref ! FakeStarting(tagName)
  }

  override def receive: Receive = {
    case _ =>
  }

}
