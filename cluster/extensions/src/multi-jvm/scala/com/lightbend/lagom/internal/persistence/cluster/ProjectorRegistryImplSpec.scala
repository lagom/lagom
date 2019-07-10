/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryActor
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryActor.DesiredStatus
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl.ProjectionMetadata
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl.Running
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

class ProjectorRegistryImplSpecMultiJvmNode1 extends ProjectorRegistryImplSpec
class ProjectorRegistryImplSpecMultiJvmNode2 extends ProjectorRegistryImplSpec
class ProjectorRegistryImplSpecMultiJvmNode3 extends ProjectorRegistryImplSpec

object ProjectorRegistryImplSpec {
  val streamName    = "test-streamName"
  val tagNamePrefix = "streamName"
  val tagName001    = s"${tagNamePrefix}001"
  val projectorName = "FakeProjector"

  val metadata001 = ProjectionMetadata(streamName, projectorName)
}

class ProjectorRegistryImplSpec extends ClusteredMultiNodeUtils with Eventually with ScalaFutures {
  import ProjectorRegistryImplSpec._
  implicit val exCtx = system.dispatcher

  "A ProjectorRegistry" must {
    "register a projector" in {

      val projectorRegistry = new ProjectorRegistryImpl(system)

      // This code will live in the driver (ReadSideImpl, Producer,...)
      val projectorProps = (projectorRegistryActorRef: ActorRef) =>
        FakeProjectorActor.props(
          projectorRegistryActorRef,
          ProjectorRegistryImplSpec.metadata001
        )

      projectorRegistry.registerProjectorGroup(
        streamName,
        shardNames = Set(tagName001),
        projectorName = projectorName,
        runInRole = None,
        projectorProps
      )

      // stop the stream
      // assert all instances stopped
      // start the stream
      // assert all instance started

      val pc = PatienceConfig(timeout = Span(20, Seconds), interval = Span(2, Seconds))

      eventually(Timeout(pc.timeout), Interval(pc.interval)) {
        whenReady(projectorRegistry.getStatus()) {
          case DesiredStatus(statuses) =>
            // find a value in the map of statuses for the `tagName001` shard.
            val tagName001Projector: Option[ProjectorRegistryImpl.ProjectorStatus] =
              statuses.filter { case (k, _) => k.tagName.contains(tagName001) }.values.headOption
            // the desired status is accessible from all nodes so we run the assertion
            // everywhere even though the actor leaves in one node.
            tagName001Projector should be(Some(Running))
        }
      }
    }
  }
}

object FakeProjectorActor {
  case object Resume
  case object Pause

  def props(projectorRegistryActor: ActorRef, projectionDetails: ProjectionMetadata): Props =
    Props(new FakeProjectorActor(projectorRegistryActor, projectionDetails))
}

// its state should be an actual copy of the desired state in the projector registry
class FakeProjectorActor(projectorRegistryActor: ActorRef, projectorMetadata: ProjectionMetadata)
    extends Actor
    with ActorLogging {

  import FakeProjectorActor._

  override def receive = {
    case EnsureActive(tagName) =>
      projectorRegistryActor ! ProjectorRegistryActor.RegisterProjector(
        projectorMetadata.copy(tagName = Some(tagName))
      )
    case Resume =>
    case Pause  =>
  }
}
