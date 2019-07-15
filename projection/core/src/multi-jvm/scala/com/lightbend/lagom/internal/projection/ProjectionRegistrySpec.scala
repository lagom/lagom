/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.lightbend.lagom.internal.cluster.ClusteredMultiNodeUtils
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.projection.ProjectionRegistry.Started
import com.lightbend.lagom.internal.projection.ProjectionRegistry.Stopped
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

class ProjectionRegistrySpecMultiJvmNode1 extends ProjectionRegistrySpec
class ProjectionRegistrySpecMultiJvmNode2 extends ProjectionRegistrySpec
class ProjectionRegistrySpecMultiJvmNode3 extends ProjectionRegistrySpec

class ProjectionRegistrySpec extends ClusteredMultiNodeUtils with Eventually with ScalaFutures {
  implicit val exCtx             = system.dispatcher
  private val pc                 = PatienceConfig(timeout = Span(20, Seconds), interval = Span(2, Seconds))
  private val projectionRegistry = new ProjectionRegistry(system)

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
      val tagNamePrefix = projectionName
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

    "pause a projection worker" in {
      enterBarrier("pause-worker-test")
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

      enterBarrier("pause-worker-test-all-nodes-ready")
      projectionRegistry.stopWorker(tagName001)
      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.findWorker(tagName001)
          maybeWorker.map(_.status) should be(Some(Stopped))
        }
      }
    }

    "pause a complete projection" in {
      enterBarrier("pause-projection-test")
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
      enterBarrier("pause-projection-test-all-nodes-ready")
      projectionRegistry.stopAllWorkers(projectionName)
      // await until seen as ready
      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val projection = x.findProjection(projectionName).get
          projection.workers.forall(_.status == Stopped) should be(true)
        }
      }
    }

  }

  private def registerProjection(streamName: String, projectionName: String, workerNames: Set[String]): Unit = {
    val projectionProps = (projectionRegistryActorRef: ActorRef) =>
      FakeProjectionActor.props(
        streamName,
        projectionName,
        projectionRegistryActorRef
      )

    projectionRegistry.registerProjectionGroup(
      streamName,
      projectionName,
      workerNames,
      runInRole = None,
      projectionProps
    )
  }
}

object FakeProjectionActor {
  case object Resume
  case object Pause

  def props(
      streamName: String,
      projectionName: String,
      projectionRegistryActorRef: ActorRef
  ): Props =
    Props(new FakeProjectionActor(streamName, projectionName, projectionRegistryActorRef))
}

// its state should be an actual copy of the desired state in the projection registry
class FakeProjectionActor(streamName: String, projectionName: String, projectionRegistryActorRef: ActorRef)
    extends Actor
    with ActorLogging {

  import FakeProjectionActor._

  override def receive = {
    case EnsureActive(tagName) =>
      projectionRegistryActorRef ! ProjectionRegistryActor.RegisterProjection(streamName, projectionName, tagName)
    case Resume =>
    case Pause  =>
  }
}
