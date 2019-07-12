/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster.projections

import akka.actor.ActorRef
import akka.cluster.ddata.PNCounterMap
import akka.actor.Props
import akka.cluster.ddata.DistributedData
import akka.actor.Terminated
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.annotation.ApiMayChange
import akka.cluster.ddata.PNCounterMapKey
import akka.cluster.ddata.Replicator.Get
import akka.cluster.ddata.Replicator.GetSuccess
import akka.cluster.ddata.Replicator.ReadLocal
import akka.cluster.ddata.Replicator.Subscribe
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata.Replicator.WriteMajority
import akka.cluster.ddata.SelfUniqueAddress
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry._

import scala.concurrent.duration._

@ApiMayChange
object ProjectionRegistryActor {
  def props = Props(new ProjectionRegistryActor)
  case class RegisterProjection(
      streamName:String,
      projectionName:String,
      workerName:String)

  // Read-Only command. Returns `DesiredState` representing the desired state of
  // the projection workers as currently seen in this node. That is not the actual
  // status of the workers (a particular order to pause/resume may be in-flight)
  // and this may may not be the latest desired state as it may have been changed
  // in other nodes and the replication may be in-flight.
  case object GetDesiredState

  case class WorkerMetadata(streamName: String, projectionName: String, workerName: String)

  /**
  {
  projections: [
    {
      name: "shopping-cart-view",
      workers: [
        { name: "shopping-cart-view-1" , state : "running" },
        { name: "shopping-cart-view-2" , state : "running" },
        { name: "shopping-cart-view-3" , state : "running" }
      ]
    },
    {
      name: "shopping-cart-kafka",
      workers: [
        { name: "shopping-cart-kafka-singleton" , state : "running" }
      ]
    }
  ]
}
    */
  @ApiMayChange
  case class DesiredState(projections: Seq[Projection])
}

class ProjectionRegistryActor extends Actor with ActorLogging {

  import ProjectionRegistryActor._
  val replicator: ActorRef             = DistributedData(context.system).replicator
  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

  // TODO: simplify into a LWWMap[WorkerMetadata, ProjectionStatus] instead of PNCounterMap?
  private val DataKey = PNCounterMapKey[WorkerMetadata]("projection-registry")
  replicator ! Subscribe(DataKey, self)

  var actorIndex: Map[WorkerMetadata, ActorRef] = Map.empty[WorkerMetadata, ActorRef]
  // required to handle Terminate(deadActor)
  var actorReverseIndex: Map[ActorRef, WorkerMetadata] = Map.empty[ActorRef, WorkerMetadata]

  override def receive: Receive = {
    case RegisterProjection(streamName, projectionName, workerName) =>
      val metadata = WorkerMetadata(streamName, projectionName, workerName)
      // when registering a projection worker, we default to state==enabled
      val writeMajority = WriteMajority(timeout = 5.seconds)
      replicator ! Update(DataKey, PNCounterMap.empty[WorkerMetadata], writeMajority)(
        //TODO: read the default state from a desired _initial state_
        _.increment(node, metadata, 1)
      )
      // keep track and watch
      actorIndex = actorIndex.updated(metadata, sender)
      actorReverseIndex = actorReverseIndex.updated(sender, metadata)
      context.watch(sender)

    case GetDesiredState =>
      replicator ! Get(DataKey, ReadLocal, Some(sender()))

    case g @ GetSuccess(DataKey, req) =>
      val registry: PNCounterMap[WorkerMetadata] = g.get(DataKey)
      val desiredStatus: DesiredState               = mapStatus(registry.entries)
      req.get.asInstanceOf[ActorRef] ! desiredStatus

    case Terminated(deadActor) =>
      // update indices and stop watching
      actorIndex = actorIndex - actorReverseIndex(deadActor)
      actorReverseIndex = actorReverseIndex - deadActor
      context.unwatch(deadActor)

    // TODO: accept state changes and propagate those state changes.
  }

  private def mapStatus(replicatedData: Map[WorkerMetadata, BigInt]): DesiredState = {

    val groupedByProjectionName: Map[String, Seq[(String, (String, BigInt))]] =
      replicatedData.toSeq.map { case (pm, bi) => (pm.projectionName, (pm.workerName, bi)) } .groupBy(_._1)
    val projections: Seq[Projection] =
      groupedByProjectionName
        .mapValues { workers: Seq[(String, (String, BigInt))] =>
          val statusPerWorker: Seq[(String, BigInt)] = workers.toMap.values.toMap.toSeq
          statusPerWorker
          // TODO: below should convert a BigInt into a valid ProjectionStatus (instead of hardcoding `Running`)
            .map{case (name, bi) => ProjectionWorker(name, Started)}
        }
        .toSeq
        .map{Projection.tupled}

    DesiredState(projections)
  }
}
