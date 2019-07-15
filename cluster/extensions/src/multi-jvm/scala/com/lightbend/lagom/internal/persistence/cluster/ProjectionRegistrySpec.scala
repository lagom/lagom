/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistryActor
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry.Started
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

class ProjectionRegistrySpecMultiJvmNode1 extends ProjectionRegistrySpec
class ProjectionRegistrySpecMultiJvmNode2 extends ProjectionRegistrySpec
class ProjectionRegistrySpecMultiJvmNode3 extends ProjectionRegistrySpec

object ProjectionRegistrySpec {
  val streamName    = "test-streamName"
  val tagNamePrefix = "streamName"
  val tagName001    = s"${tagNamePrefix}001"
  val projectionName = "FakeProjection"
}

class ProjectionRegistrySpec extends ClusteredMultiNodeUtils with Eventually with ScalaFutures {
  import ProjectionRegistrySpec._
  implicit val exCtx = system.dispatcher

  "A ProjectionRegistry" must {
    "register a projection" in {

      val projectionRegistry = new ProjectionRegistry(system)

      // This code will live in the driver (ReadSideImpl, Producer,...)
      val projectionProps = (projectionRegistryActorRef: ActorRef) =>
        FakeProjectionActor.props(
          streamName,
          projectionName,
          projectionRegistryActorRef
        )

      projectionRegistry.registerProjectionGroup(
        streamName,
        shardNames = Set(tagName001),
        projectionName = projectionName,
        runInRole = None,
        projectionProps
      )

      // stop the stream
      // assert all instances stopped
      // start the stream
      // assert all instance started

      val pc = PatienceConfig(timeout = Span(20, Seconds), interval = Span(2, Seconds))

      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectionRegistry.getStatus()) { x =>
          val maybeWorker = x.projections.flatMap(_.workers.find(_.name.contains(tagName001))).headOption
          maybeWorker.map(_.status) should be(Some(Started))
        }
      }
    }
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
