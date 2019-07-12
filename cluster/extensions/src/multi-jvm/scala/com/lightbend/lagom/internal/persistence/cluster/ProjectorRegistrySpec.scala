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
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry.Running
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

class ProjectorRegistrySpecMultiJvmNode1 extends ProjectorRegistrySpec
class ProjectorRegistrySpecMultiJvmNode2 extends ProjectorRegistrySpec
class ProjectorRegistrySpecMultiJvmNode3 extends ProjectorRegistrySpec

object ProjectorRegistrySpec {
  val streamName    = "test-streamName"
  val tagNamePrefix = "streamName"
  val tagName001    = s"${tagNamePrefix}001"
  val projectorName = "FakeProjector"
}

class ProjectorRegistrySpec extends ClusteredMultiNodeUtils with Eventually with ScalaFutures {
  import ProjectorRegistrySpec._
  implicit val exCtx = system.dispatcher

  "A ProjectorRegistry" must {
    "register a projector" in {

      val projectorRegistry = new ProjectorRegistry(system)

      // This code will live in the driver (ReadSideImpl, Producer,...)
      val projectorProps = (projectorRegistryActorRef: ActorRef) =>
        FakeProjectorActor.props(
          streamName,
          projectorName,
          projectorRegistryActorRef
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
        whenReady(projectorRegistry.getStatus()) { x =>
          val maybeWorker = x.projectors.flatMap(_.workers.find(_.name.contains(tagName001))).headOption
          maybeWorker.map(_.status) should be(Some(Running))
        }
      }
    }
  }
}

object FakeProjectorActor {
  case object Resume
  case object Pause

  def props(
      streamName: String,
      projectorName: String,
      projectorRegistryActorRef: ActorRef
  ): Props =
    Props(new FakeProjectorActor(streamName, projectorName, projectorRegistryActorRef))
}

// its state should be an actual copy of the desired state in the projector registry
class FakeProjectorActor(streamName: String, projectorName: String, projectorRegistryActorRef: ActorRef)
    extends Actor
    with ActorLogging {

  import FakeProjectorActor._

  override def receive = {
    case EnsureActive(tagName) =>
      projectorRegistryActorRef ! ProjectorRegistryActor.RegisterProjector(streamName, projectorName, tagName)
    case Resume =>
    case Pause  =>
  }
}
