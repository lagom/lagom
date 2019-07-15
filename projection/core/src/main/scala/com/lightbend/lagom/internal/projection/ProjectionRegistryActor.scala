/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.ddata.DistributedData
import akka.actor.Terminated
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.annotation.ApiMayChange
import akka.cluster.ddata.LWWMap
import akka.cluster.ddata.LWWMapKey
import akka.cluster.ddata.Replicator.Changed
import akka.cluster.ddata.Replicator.ReadConsistency
import akka.cluster.ddata.Replicator.ReadLocal
import akka.cluster.ddata.Replicator.Subscribe
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata.Replicator.WriteConsistency
import akka.cluster.ddata.Replicator.WriteMajority
import akka.cluster.ddata.SelfUniqueAddress
import com.lightbend.lagom.internal.projection.ProjectionRegistry._

import scala.concurrent.duration._

@ApiMayChange
object ProjectionRegistryActor {
  def props = Props(new ProjectionRegistryActor)
  case class RegisterProjection(streamName: String, projectionName: String, workerName: String)

  // TODO: rename to WorkerCoordinates
  // TODO: remove `streamName`
  case class WorkerMetadata(streamName: String, projectionName: String, workerName: String)

  // Read-Only command. Returns `DesiredState` representing the desired state of
  // the projection workers as currently seen in this node. That is not the actual
  // status of the workers (a particular order to pause/resume may be in-flight)
  // and this may may not be the latest desired state as it may have been changed
  // in other nodes and the replication may be in-flight.
  case object GetDesiredState

  /**
   *{
   *projections: [
   *{
   *name: "shopping-cart-view",
   *workers: [
   *{ name: "shopping-cart-view-1" , state : "running" },
   *{ name: "shopping-cart-view-2" , state : "running" },
   *{ name: "shopping-cart-view-3" , state : "running" }
   *]
   *},
   *{
   *name: "shopping-cart-kafka",
   *workers: [
   *{ name: "shopping-cart-kafka-singleton" , state : "running" }
   *]
   *}
   *]
   *}
   */
  @ApiMayChange
  case class DesiredState(projections: Seq[Projection]) {
    def findProjection(projectionName: String): Option[Projection] =
      projections.find(_.name == projectionName)

    def findWorker(workerName: String): Option[ProjectionWorker] =
      projections.flatMap(_.workers).find(_.name == workerName)
  }

  object DesiredState{
    private[lagom] def fromReplicatedData(replicatedData: Map[WorkerMetadata, WorkerStatus]): DesiredState = {

      val groupedByProjectionName: Map[String, Seq[(String, (String, WorkerStatus))]] =
        replicatedData.toSeq.map { case (pm, ws) => (pm.projectionName, (pm.workerName, ws)) }.groupBy(_._1)
      val projections: Seq[Projection] =
        groupedByProjectionName
          .mapValues {
            _.map(_._2).map { case (name, ws) => ProjectionWorker(name, ws) }
          }
          .toSeq
          .map { Projection.tupled }

      DesiredState(projections)
    }

  }
}

class ProjectionRegistryActor extends Actor with ActorLogging {

  import ProjectionRegistryActor._

  type WorkerName = String

  val replicator: ActorRef             = DistributedData(context.system).replicator
  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

  // (a) Replicator contains data of all workers
  private val DataKey: LWWMapKey[WorkerMetadata, WorkerStatus] =
    LWWMapKey[WorkerMetadata, WorkerStatus]("projection-registry")
  // TODO: improvement, use a DataKey per projection so replicated datasets are smaller (https://doc.akka.io/docs/akka/current/distributed-data.html#maps)
  replicator ! Subscribe(DataKey, self)
  val writeMajority: WriteConsistency  = WriteMajority(timeout = 5.seconds)
  val readConsistency: ReadConsistency = ReadLocal

  // (b) Keep a local copy to simplify thhe implementation of some ops
  var localCopy: Map[WorkerMetadata, WorkerStatus] = Map.empty[WorkerMetadata, WorkerStatus]
  def nameIndex: Map[WorkerName, WorkerMetadata] = localCopy.keySet.map(wm => wm.workerName -> wm).toMap

  // (c) Actor indices contain only data of workers running locally
  // TODO: trust WorkerName is unique and use WorkerName as key
  var actorIndex: Map[WorkerName, ActorRef] = Map.empty[WorkerName, ActorRef]
  // required to handle Terminate(deadActor)
  var actorReverseIndex: Map[ActorRef, WorkerName] = Map.empty[ActorRef, WorkerName]

  override def receive: Receive = {
    case RegisterProjection(streamName, projectionName, workerName) =>
      val metadata = WorkerMetadata(streamName, projectionName, workerName)
      // when registering a projection worker, we default to state==enabled
      updateLWWMap(metadata, Started)
      // keep track
      actorIndex = actorIndex.updated(workerName, sender)
      actorReverseIndex = actorReverseIndex.updated(sender, workerName)
      // watch
      context.watch(sender)

    case GetDesiredState =>
      val state = DesiredState.fromReplicatedData(localCopy)
      sender ! state
      // improvement: use Replicator.FlushChanges before Get to get latest version

    case Stop(workerName) =>
      requestStop(workerName)

//    case g @ GetSuccess(DataKey, req) =>
//    case g @ GetFailure(DataKey, req) => ??? // TODO
//    case g @ NotFound                 => ??? // TODO

    case changed @ Changed(DataKey) =>
      localCopy = changed.get(DataKey).entries


    case Terminated(deadActor) =>
      // update indices and stop watching
      actorIndex = actorIndex - actorReverseIndex(deadActor)
      actorReverseIndex = actorReverseIndex - deadActor
      context.unwatch(deadActor)

  }

  private def updateLWWMap(workerMetadata: WorkerMetadata, workerStatus: WorkerStatus) = {
    replicator ! Update(DataKey, LWWMap.empty[WorkerMetadata, WorkerStatus], writeMajority)(
      _.:+(workerMetadata -> workerStatus)
    )
  }

  private def requestStop(workerName: WorkerName)  = {
    val workerMetadata = nameIndex.getOrElse(workerName, throw ProjectionWorkerNotFound(workerName))
    updateLWWMap(workerMetadata, Stopped)
  }
  private def requestStart(workerName: WorkerName) = {}
}
