/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.projections

import akka.actor.ActorRef
import akka.cluster.ddata.PNCounterMap
import akka.actor.Props
import akka.cluster.ddata.DistributedData
import akka.actor.Terminated
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.ddata.PNCounterMapKey
import akka.cluster.ddata.Replicator.Get
import akka.cluster.ddata.Replicator.GetSuccess
import akka.cluster.ddata.Replicator.ReadLocal
import akka.cluster.ddata.Replicator.Subscribe
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata.Replicator.WriteMajority
import com.lightbend.lagom.internal.persistence.projections.ProjectorRegistry._

import scala.concurrent.duration._

object ProjectorRegistryActor {
  def props: Props = Props(new ProjectorRegistryActor)
  case class RegisterProjector(projectorActor: ActorRef, metadata: ProjectionMetadata)

  // Read-Only command. Returns `Map[ProjectionMetadata, ProjectorStatus]` representing the desired
  // status as currently seen in this node. That is not the actual status and may not be the latest
  // desired status.
  case object GetStatus
}

class ProjectorRegistryActor extends Actor with ActorLogging {

  import ProjectorRegistryActor._
  val replicator: ActorRef = DistributedData(context.system).replicator
  implicit val node        = DistributedData(context.system).selfUniqueAddress

  // TODO: simplify into a LWWMap[ProjectionMetadata, ProjectorStatus] instead of PNCounterMap?
  val DataKey = PNCounterMapKey[ProjectionMetadata]("projector-registry")
  replicator ! Subscribe(DataKey, self)

  var actorIndex: Map[ProjectionMetadata, ActorRef] = Map.empty[ProjectionMetadata, ActorRef]
  // required to handle Terminate(deadActor)
  var actorReverseIndex: Map[ActorRef, ProjectionMetadata] = Map.empty[ActorRef, ProjectionMetadata]

  override def receive = {
    case RegisterProjector(projectorActor, metadata) =>
      // when registering a projector worker, we default to state==enabled
      val writeMajority = WriteMajority(timeout = 5.seconds)
      replicator ! Update(DataKey, PNCounterMap.empty[ProjectionMetadata], writeMajority)(
        //TODO: read the default state from a desired _initial state_
        _.increment(node, metadata, 1)
      )
      // keep track and watch
      actorIndex = actorIndex.updated(metadata, projectorActor)
      actorReverseIndex = actorReverseIndex.updated(projectorActor, metadata)
      context.watch(projectorActor)

    case GetStatus =>
      replicator ! Get(DataKey, ReadLocal, Some(sender()))

    case g @ GetSuccess(DataKey, req) =>
      val registry: PNCounterMap[ProjectionMetadata]                                = g.get(DataKey)
      val desiredStatus: Map[ProjectionMetadata, ProjectorRegistry.ProjectorStatus] =
        // TODO: map the value of the counter (0 or 1) to stopping/running.
        registry.entries.keySet.map((_, Running)).toMap
      req.get.asInstanceOf[ActorRef] ! desiredStatus

    case Terminated(deadActor) =>
      // update indices and stop watching
      actorIndex = actorIndex - actorReverseIndex(deadActor)
      actorReverseIndex = actorReverseIndex - deadActor
      context.unwatch(deadActor)

    // TODO: accept state changes and propagate those state changes.

  }
}
