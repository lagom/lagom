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
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped

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
  case object GetState

}

class ProjectionRegistryActor extends Actor with ActorLogging {

  import ProjectionRegistryActor._

  type WorkerName = String

  val replicator: ActorRef             = DistributedData(context.system).replicator
  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
  // TODO: make timeout configurable (5.second is too aggressive in big clusters)
  val writeMajority: WriteConsistency  = WriteMajority(timeout = 5.seconds)
  val readConsistency: ReadConsistency = ReadLocal

  // (a) Replicator contains data of all workers
  private val RequestedStatusDataKey: LWWMapKey[WorkerName, Status] =
    LWWMapKey[WorkerName, Status]("projection-registry-desired-status")
  private val ObservedStatusDataKey: LWWMapKey[WorkerName, Status] =
    LWWMapKey[WorkerName, Status]("projection-registry-observed-status")
  private val NameIndexDataKey: LWWMapKey[WorkerName, WorkerMetadata] =
    LWWMapKey[WorkerName, WorkerMetadata]("projection-registry-name-index")
  replicator ! Subscribe(RequestedStatusDataKey, self)
  replicator ! Subscribe(ObservedStatusDataKey, self)
  replicator ! Subscribe(NameIndexDataKey, self)

  // (b) Keep a local copy to simplify the implementation of some ops
  var requestedStatusLocalCopy: Map[WorkerName, Status] = Map.empty[WorkerName, Status]
  var observedStatusLocalCopy: Map[WorkerName, Status]  = Map.empty[WorkerName, Status]

  var nameIndexLocalCopy: Map[WorkerName, WorkerMetadata] = Map.empty[WorkerName, WorkerMetadata]

  // (c) Actor indices contain only data of workers running locally
  // TODO: trust WorkerName is unique and use WorkerName as key
  var actorIndex: Map[WorkerName, ActorRef] = Map.empty[WorkerName, ActorRef]
  // required to handle Terminate(deadActor)
  var reversedActorIndex: Map[ActorRef, WorkerName] = Map.empty[ActorRef, WorkerName]

  override def receive: Receive = {

    case RegisterProjection(streamName, projectionName, workerName) =>
      val metadata = WorkerMetadata(streamName, projectionName, workerName)
      // when registering a projection worker, we default to state==started
      updateLWWMapForRequests(workerName, Started)
      updateLWWMapForObserved(workerName, Started)
      updateLWWMapForNameIndex(workerName, metadata)
      // keep track
      actorIndex = actorIndex.updated(workerName, sender)
      reversedActorIndex = reversedActorIndex.updated(sender, workerName)
      // watch
      context.watch(sender)

    case GetState =>
      sender ! State.fromReplicatedData(nameIndexLocalCopy, requestedStatusLocalCopy, observedStatusLocalCopy)

    case command: StateRequestCommand =>
      // locate the target actor and send the request
      request(command)

    case observedStatus: Status =>
      reversedActorIndex.get(sender()).foreach(workerName => updateLWWMapForObserved(workerName, observedStatus))

    // TODO: handle UpdateSuccess/UpdateFailure !!
    case changed @ Changed(RequestedStatusDataKey) => {

      val remotelyChanged                  = changed.get(RequestedStatusDataKey).entries
      val diffs: Set[(WorkerName, Status)] = remotelyChanged.toSet.diff(requestedStatusLocalCopy.toSet)

      diffs
        .foreach {
          case (workerName, requestedStatus) =>
            actorIndex.get(workerName).foreach { workerRef =>
              workerRef ! requestedStatus
            }
        }

      requestedStatusLocalCopy = remotelyChanged
    }

    // TODO: handle UpdateSuccess/UpdateFailure !!
    case changed @ Changed(ObservedStatusDataKey) =>
      observedStatusLocalCopy = changed.get(ObservedStatusDataKey).entries

    // TODO: handle UpdateSuccess/UpdateFailure !!
    case changed @ Changed(NameIndexDataKey) =>
      nameIndexLocalCopy = changed.get(NameIndexDataKey).entries

    case Terminated(deadActor) =>
      // when a watched actor dies, we marked it as stopped...
      reversedActorIndex.get(deadActor).foreach { name =>
        updateLWWMapForObserved(name, Stopped)
      }
      // ... and then update indices and stop watching
      actorIndex = actorIndex - reversedActorIndex(deadActor)
      reversedActorIndex = reversedActorIndex - deadActor
      context.unwatch(deadActor)

  }

  private def updateLWWMapForRequests(workerMetadata: WorkerName, requested: Status): Unit = {
    replicator ! Update(RequestedStatusDataKey, LWWMap.empty[WorkerName, Status], writeMajority)(
      _.:+(workerMetadata -> requested)
    )
  }

  private def updateLWWMapForObserved(workerName: WorkerName, status: Status): Unit = {
    replicator ! Update(ObservedStatusDataKey, LWWMap.empty[WorkerName, Status], writeMajority)(
      _.:+(workerName -> status)
    )
  }

  private def updateLWWMapForNameIndex(workerName: WorkerName, metadata: WorkerMetadata): Unit = {
    replicator ! Update(NameIndexDataKey, LWWMap.empty[WorkerName, WorkerMetadata], writeMajority)(
      _.:+(workerName -> metadata)
    )
  }

  private def request(command: StateRequestCommand): Unit = {
    log.warning(s"Sending request $command to worker")
    updateLWWMapForRequests(command.workerName, command.requested)
  }

}
