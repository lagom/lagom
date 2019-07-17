
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
import com.lightbend.lagom.internal.projection.FakeProjectionActor.Stopping
import com.lightbend.lagom.internal.projection.ProjectionRegistry.Started
import com.lightbend.lagom.internal.projection.ProjectionRegistry.Stopped
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
          maybeWorker.map(_.status) should be(Some(Started))
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
          maybeWorker.map(_.status) should be(Some(Started))
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
          maybeWorker.map(_.status) should be(Some(Started))
        }
      }

      enterBarrier("request-pause-worker-test-all-nodes-ready")
      projectionRegistry.stopWorker(tagName001)
      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.status) should be(Some(Stopped))
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
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.status) should be(Some(Started))
        }
      }
      enterBarrier("request-pause-projection-test-all-nodes-ready")
      projectionRegistry.stopAllWorkers(projectionName)
      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val projection = x.findProjection(projectionName).get
          projection.workers.forall(_.status == Stopped) should be(true)
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

      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.status) should be(Some(Started))
        }
      }

      enterBarrier("do-pause-worker-test-all-nodes-ready")
      runOn(RoleName("node2")) {
        projectionRegistry.stopWorker(tagName001)
      }

      val multiNodeExpect = new MultiNodeExpect(testProbe)
      val multiExpectFuture =
        multiNodeExpect.expectMsg(Stopping(tagName001), "do-pause-worker-test-expect-stopped", multiExpectTimeout)
      Await.result(multiExpectFuture, multiExpectTimeout) shouldBe Done
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

  case class Stopping(tagName: String)

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

  var tagName = ""

  override def receive = {
    case EnsureActive(tagName) =>
      this.tagName = tagName
      projectionRegistryActorRef ! ProjectionRegistryActor.RegisterProjection(streamName, projectionName, tagName)
    case Stopped =>
      testProbe ! Stopping(tagName)
  }
}
