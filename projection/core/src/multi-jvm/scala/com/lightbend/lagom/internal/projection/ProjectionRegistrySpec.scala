/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.Done
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.remote.testconductor.RoleName
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.cluster.ClusteredMultiNodeUtils
import com.lightbend.lagom.internal.cluster.MultiNodeExpect
import com.lightbend.lagom.internal.projection.FakeProjectionActor.FakeStarting
import com.lightbend.lagom.internal.projection.FakeProjectionActor.FakeStopping
import com.lightbend.lagom.projection.Projection
import com.lightbend.lagom.projection.Started
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

class ProjectionRegistrySpec extends ClusteredMultiNodeUtils with Eventually with ScalaFutures {
  implicit val exCtx             = system.dispatcher
  private val pc                 = PatienceConfig(timeout = Span(20, Seconds), interval = Span(2, Seconds))
  private val projectionRegistry = new ProjectionRegistry(system)
  private val noMessageTimeout   = 7.seconds
  // distributed expect ops need a generous timeout
  private val multiExpectTimeout = 30.seconds

  "A ProjectionRegistry" must {

    "register a projection with a single worker" in {
      val tagNamePrefix = "streamName"
      val tagName001    = s"${tagNamePrefix}001"

      registerProjection("some-stream", "test-canary", Set(tagName001))

      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.observedStatus) should be(Some(Started))
        }
      }
    }
    "register a projection with multiple workers" in {
      val projectionName = "test-canary-many-workers"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head

      registerProjection("some-stream", projectionName, tagNames.toSet)

      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.observedStatus) should be(Some(Started))
        }
      }
    }

    "request the pause of a projection worker" in {
      enterBarrier("request-pause-worker-test")
      val projectionName = "test-pause-worker"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head

      registerProjection("some-stream", projectionName, tagNames.toSet)

      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.observedStatus) should be(Some(Started))
        }
      }

      enterBarrier("request-pause-worker-test-all-nodes-ready")
      projectionRegistry.stopWorker(tagName001)
      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeRequested = x.findWorker(tagName001).map(_.requestedStatus)
          val maybeObserved  = x.findWorker(tagName001).map(_.observedStatus)
          maybeRequested shouldBe Some(Stopped)
          maybeObserved shouldBe Some(Stopped)
        }
      }
    }

    "request the pause of a complete projection" in {
      enterBarrier("request-pause-projection-test")
      val projectionName = "test-pause-projection"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head

      registerProjection("some-stream", projectionName, tagNames.toSet)

      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val projection = x.findProjection(projectionName).get
          projection.workers.forall(_.requestedStatus.isInstanceOf[Started]) should be(true)
        }
      }
      // Don't try to `stopWorkers` until we've seen `desired` propagate completely
      enterBarrier("request-pause-projection-test-all-nodes-ready")
      projectionRegistry.stopAllWorkers(projectionName)
      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val projection: Projection = x.findProjection(projectionName).get
          projection.workers.forall(_.requestedStatus.isInstanceOf[Stopped]) should be(true)
        }
      }
    }

    "tell a projection worker to stop when requested" in {
      enterBarrier("do-pause-worker-test")
      val projectionName = "do-pause-worker"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head

      // build a projection with a single worker bound to run on `node3`
      val testProbe = registerProjection("some-stream", projectionName, tagNames.toSet)
      testProbe.ignoreMsg {
        case FakeStopping(_) => false
        case _               => true
      }

      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.observedStatus) should be(Some(Started))
        }
      }

      enterBarrier("do-pause-worker-test-all-nodes-ready")
      runOn(RoleName("node2")) {
        projectionRegistry.stopWorker(tagName001)
      }

      val multiNodeExpect = new MultiNodeExpect(testProbe)
      val multiExpectFuture =
        multiNodeExpect.expectMsg(FakeStopping(tagName001), "do-pause-worker-test-expect-stopping", multiExpectTimeout)
      Await.result(multiExpectFuture, multiExpectTimeout) shouldBe Done
    }

    "tell a projection worker to stop and then start when requested" in {
      enterBarrier("do-pause-and-resume-worker-test")
      val projectionName = "do-pause-and-resume-worker"
      val tagNamePrefix  = projectionName
      val tagNames       = (1 to 5).map(id => s"$tagNamePrefix-$id")
      val tagName001     = tagNames.head

      // build a projection with a single worker bound to run on `node3`
      val testProbe = registerProjection("some-stream", projectionName, tagNames.toSet)
      testProbe.ignoreMsg {
        case FakeStopping(_) => false
        case _               => true
      }

      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.observedStatus) should be(Some(Started))
        }
      }

      enterBarrier("do-pause-and-resume-worker-test-all-nodes-ready-001")
      runOn(RoleName("node2")) {
        projectionRegistry.stopWorker(tagName001)
      }

      val multiNodeExpect = new MultiNodeExpect(testProbe)
      val expectStopping =
        multiNodeExpect.expectMsg(
          FakeStopping(tagName001),
          "do-pause-and-resume-worker-test-expect-stopping",
          multiExpectTimeout
        )
      Await.result(expectStopping, multiExpectTimeout)
      enterBarrier("do-pause-and-resume-worker-test-all-nodes-ready-002")

      testProbe.ignoreNoMsg()
      runOn(RoleName("node3")) {
        projectionRegistry.startWorker(tagName001)
      }

      val expectStarting =
        multiNodeExpect.expectMsg(
          FakeStarting(tagName001),
          "do-pause-and-resume-worker-test-expect-starting",
          multiExpectTimeout
        )
      Await.result(expectStarting, multiExpectTimeout) shouldBe Done
    }

  }

  private def registerProjection(
      streamName: String,
      projectionName: String,
      workerNames: Set[String],
      runInRole: Option[String] = None
  ): TestProbe = {
    val testProbe = TestProbe()
    val projectionProps = (projectionRegistryActorRef: ActorRef) =>
      FakeProjectionActor.props(
        streamName,
        projectionName,
        projectionRegistryActorRef,
        testProbe.ref
      )

    projectionRegistry.registerProjectionGroup(
      streamName,
      projectionName,
      workerNames,
      runInRole,
      projectionProps
    )
    testProbe
  }
}

object FakeProjectionActor {

  case class FakeStopping(tagName: String)
  case class FakeStarting(tagName: String)

  def props(
      streamName: String,
      projectionName: String,
      projectionRegistryActorRef: ActorRef,
      testProbe: ActorRef
  ): Props =
    Props(new FakeProjectionActor(streamName, projectionName, projectionRegistryActorRef, testProbe))
}

// its state should be an actual copy of the desired state in the projection registry
class FakeProjectionActor(
    streamName: String,
    projectionName: String,
    projectionRegistryActorRef: ActorRef,
    testProbe: ActorRef
) extends Actor
    with ActorLogging {

  override def preStart(): Unit = {
    super.preStart()
  }

  override def receive: Receive = {
    case EnsureActive(tagName) =>
      projectionRegistryActorRef ! ProjectionRegistryActor.RegisterProjection(streamName, projectionName, tagName)
      context.become(active(tagName))
  }

  private def active(tagName: String): Receive = {
    case EnsureActive(_) =>
    // yes, we're active
    case Stopped =>
      testProbe ! FakeStopping(tagName)
      projectionRegistryActorRef ! Stopped
    case Started =>
      testProbe ! FakeStarting(tagName)
      projectionRegistryActorRef ! Started
  }
}
