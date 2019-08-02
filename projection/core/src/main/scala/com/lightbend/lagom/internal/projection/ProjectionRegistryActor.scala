/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import java.net.URLEncoder

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
import akka.cluster.ddata.Replicator.UpdateFailure
import akka.cluster.ddata.Replicator.UpdateSuccess
import akka.cluster.ddata.Replicator.WriteConsistency
import akka.cluster.ddata.Replicator.WriteMajority
import akka.cluster.ddata.SelfUniqueAddress
import com.lightbend.lagom.internal.projection.ProjectionRegistry._
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped

import scala.concurrent.duration._
import com.lightbend.lagom.projection.ProjectionSerializable

@ApiMayChange
object ProjectionRegistryActor {
  def props = Props(new ProjectionRegistryActor)

  case class RegisterProjectionWorker(coordinates: WorkerCoordinates)

  case class WorkerCoordinates(projectionName: String, tagName: String) extends ProjectionSerializable {
    val asKey: String                = s"$projectionName-$tagName"
    val workerActorName: String      = URLEncoder.encode(asKey, "utf-8")
    val supervisingActorName: String = URLEncoder.encode(s"backoff-$asKey", "utf-8")
  }

  // Read-Only command. Returns `State` representing the state of
  // the projection workers as currently seen in this node. It contains both the
  // requested and the observed status for each worker (both are eventually consistent
  // values since both may have been edited concurrently in other nodes).
  case object GetState

  val DefaultInitialStatus: Status = Started

}

class ProjectionRegistryActor extends Actor with ActorLogging {

  import ProjectionRegistryActor._

  type WorkerKey = String // a WorkerKey is a unique String representing WorkerCoordinates

  val replicator: ActorRef             = DistributedData(context.system).replicator
  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
  // TODO: make timeout configurable (5.second is too aggressive in big clusters)
  val writeMajority: WriteConsistency  = WriteMajority(timeout = 5.seconds)
  val readConsistency: ReadConsistency = ReadLocal

  // (a) Replicator contains data of all workers
  private val RequestedStatusDataKey: LWWMapKey[WorkerKey, Status] =
    LWWMapKey[WorkerKey, Status]("projection-registry-desired-status")
  private val ObservedStatusDataKey: LWWMapKey[WorkerKey, Status] =
    LWWMapKey[WorkerKey, Status]("projection-registry-observed-status")
  private val NameIndexDataKey: LWWMapKey[WorkerKey, WorkerCoordinates] =
    LWWMapKey[WorkerKey, WorkerCoordinates]("projection-registry-name-index")
  replicator ! Subscribe(RequestedStatusDataKey, self)
  replicator ! Subscribe(ObservedStatusDataKey, self)
  replicator ! Subscribe(NameIndexDataKey, self)

  // (b) Keep a local copy to simplify the implementation of some ops
  var requestedStatusLocalCopy: Map[WorkerKey, Status] = Map.empty[WorkerKey, Status]
  var observedStatusLocalCopy: Map[WorkerKey, Status]  = Map.empty[WorkerKey, Status]

  var nameIndexLocalCopy: Map[WorkerKey, WorkerCoordinates] = Map.empty[WorkerKey, WorkerCoordinates]

  // (c) Actor indices contain only data of workers running locally
  var actorIndex: Map[WorkerKey, ActorRef] = Map.empty[WorkerKey, ActorRef]
  // required to handle Terminate(deadActor)
  var reversedActorIndex: Map[ActorRef, WorkerKey] = Map.empty[ActorRef, WorkerKey]

  override def receive: Receive = {

    case RegisterProjectionWorker(coordinates) =>
      log.debug(s"Registering worker $coordinates to [${sender().path.toString}]")
      // keep track
      val workerKey: WorkerKey = coordinates.asKey
      updateLWWMapForNameIndex(workerKey, coordinates)
      actorIndex = actorIndex.updated(workerKey, sender())
      reversedActorIndex = reversedActorIndex.updated(sender, workerKey)
      // when worker registers, we must reply with the requested status (if it's been set already, or Started if not).
      // TODO: parameterize the default initial status
      val initialStatus = requestedStatusLocalCopy.getOrElse(workerKey, DefaultInitialStatus)
      log.debug(s"Setting initial status [$initialStatus] on worker $workerKey [${sender().path.toString}]")
      sender ! initialStatus

      // watch
      context.watch(sender)

    case GetState =>
      sender ! State.fromReplicatedData(
        nameIndexLocalCopy,
        requestedStatusLocalCopy,
        observedStatusLocalCopy
      )(
        DefaultInitialStatus,
        Stopped
      )

    // StateRequestCommand come from `ProjectionRegistry` and contain a requested Status
    case command: StateRequestCommand =>
      // locate the target actor and send the request
      log.debug(s"Propagating request $command.")
      updateLWWMapForRequests(command.coordinates.asKey, command.requestedStatus)

    // Bare Status come from worker and contain an observed Status
    case observedStatus: Status =>
      log.debug(s"Observed [${sender().path.toString}] as $observedStatus.")
      reversedActorIndex.get(sender()) match {
        case Some(workerName) => updateLWWMapForObserved(workerName, observedStatus)
        case None             => log.error(s"Unknown actor [${sender().path.toString}] reports status $observedStatus.")
      }

    // TODO: handle UpdateSuccess/UpdateFailure !!
    case UpdateSuccess(_, _) => //TODO
    case _: UpdateFailure[_] => //TODO
    case changed @ Changed(RequestedStatusDataKey) => {
      val remotelyChanged                 = changed.get(RequestedStatusDataKey).entries
      val diffs: Set[(WorkerKey, Status)] = remotelyChanged.toSet.diff(requestedStatusLocalCopy.toSet)

      // when the requested status changes, we must forward the new value to the appropriate actor
      // if it's a one of the workers in the local actorIndex
      diffs
        .foreach {
          case (workerName, requestedStatus) =>
            log.debug(s"Remotely requested worker [$workerName] as [$requestedStatus].")
            actorIndex.get(workerName).foreach { workerRef =>
              log.debug(
                s"Setting requested status [$requestedStatus] on worker $workerName [${workerRef.path.toString}]"
              )
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
      log.debug(s"Worker ${deadActor.path.name} died. Marking it as Stopped.")
      // when a watched actor dies, we mark it as stopped. It will eventually
      // respawn (thanks to EnsureActive) and come back to it's requested status.
      reversedActorIndex.get(deadActor).foreach { name =>
        updateLWWMapForObserved(name, Stopped)
      }
      // ... and then update indices and stop watching
      actorIndex = actorIndex - reversedActorIndex(deadActor)
      reversedActorIndex = reversedActorIndex - deadActor

  }

  private def updateLWWMapForRequests(workerMetadata: WorkerKey, requested: Status): Unit = {
    replicator ! Update(RequestedStatusDataKey, LWWMap.empty[WorkerKey, Status], writeMajority)(
      _.:+(workerMetadata -> requested)
    )
  }

  private def updateLWWMapForObserved(workerName: WorkerKey, status: Status): Unit = {
    replicator ! Update(ObservedStatusDataKey, LWWMap.empty[WorkerKey, Status], writeMajority)(
      _.:+(workerName -> status)
    )
  }

  private def updateLWWMapForNameIndex(workerName: WorkerKey, metadata: WorkerCoordinates): Unit = {
    replicator ! Update(NameIndexDataKey, LWWMap.empty[WorkerKey, WorkerCoordinates], writeMajority)(
      _.:+(workerName -> metadata)
    )
  }

}
