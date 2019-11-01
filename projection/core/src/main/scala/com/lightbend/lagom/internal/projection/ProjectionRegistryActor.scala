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
import akka.cluster.ddata.Replicator.Subscribe
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata.Replicator.UpdateFailure
import akka.cluster.ddata.Replicator.UpdateSuccess
import akka.cluster.ddata.Replicator.WriteConsistency
import akka.cluster.ddata.Replicator.WriteMajority
import akka.cluster.ddata.SelfUniqueAddress
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped

import scala.concurrent.duration._
import com.lightbend.lagom.projection.ProjectionSerializable

@ApiMayChange
object ProjectionRegistryActor {
  def props = Props(new ProjectionRegistryActor)

  /** a WorkerKey is a unique String representing WorkerCoordinates */
  type WorkerKey      = String
  type ProjectionName = String

  case class WorkerRequestCommand(coordinates: WorkerCoordinates, requestedStatus: Status)
  case class ProjectionRequestCommand(projectionName: ProjectionName, requestedStatus: Status)

  case class RegisterProjection(projectionName: ProjectionName, tagNames: Set[String])
  case class ReportForDuty(coordinates: WorkerCoordinates)

  /**
   * Uniquely identify a worker.
   * @param projectionName the projection this worker is a part of
   * @param tagName the name of the tag this worker is responsible for consuming
   */
  case class WorkerCoordinates(projectionName: ProjectionName, tagName: String) extends ProjectionSerializable {
    val asKey: WorkerKey             = s"$projectionName-$tagName"
    val workerActorName: String      = URLEncoder.encode(asKey, "utf-8")
    val supervisingActorName: String = URLEncoder.encode(s"backoff-$asKey", "utf-8")
  }

  /**
   * Read-Only command. Returns `State` representing the state of the projection workers as
   * currently seen in this node. It contains both the requested and the observed status for
   * each worker (both are eventually consistent values since both may have been edited
   * concurrently in other nodes).
   */
  case object GetState
}

/**
 * Build an in-memory, CRDT-backed representation of the status of each projection worker. Each
 * instance of this actor communicates with the {{{WorkerCoordinator}}} running locally to propagate
 * requests and retrieve status of the actual worker actor. The retrieved data for requested and
 * observed status is propagated to other peers.
 *
 * See also https://github.com/playframework/play-meta/blob/master/docs/design/projections-design.md
 */
class ProjectionRegistryActor extends Actor with ActorLogging {
  import ProjectionRegistryActor._

  val replicator: ActorRef             = DistributedData(context.system).replicator
  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

  private val projectionConfig: ProjectionConfig = ProjectionConfig(context.system.settings.config)

  // All usages of `ddata` in this actor are unaffected by `UpdateTimeout` (see
  //   https://github.com/lagom/lagom/pull/2208). In general uses, using WriteMajority(5 sec) could be an issue
  //   in big clusters but given the nature and how often the data stored here is modified, WriteMajority
  //   with a 5sec timeout should be fine even in big clusters.
  val writeConsistency: WriteConsistency = WriteMajority(timeout = projectionConfig.writeMajorityTimeout)

  // (a) Replicator contains data of all workers (requested and observed status, plus a name index)
  private val RequestedStatusDataKey: LWWMapKey[WorkerCoordinates, Status] =
    LWWMapKey[WorkerCoordinates, Status]("projection-registry-requested-status")
  private val ObservedStatusDataKey: LWWMapKey[WorkerCoordinates, Status] =
    LWWMapKey[WorkerCoordinates, Status]("projection-registry-observed-status")

  replicator ! Subscribe(RequestedStatusDataKey, self)
  replicator ! Subscribe(ObservedStatusDataKey, self)

  // (b) Keep a local copy to simplify the implementation of some ops
  var requestedStatusLocalCopy: Map[WorkerCoordinates, Status] = Map.empty[WorkerCoordinates, Status]
  var observedStatusLocalCopy: Map[WorkerCoordinates, Status]  = Map.empty[WorkerCoordinates, Status]

  // (c) Actor indices contain only data of workers running locally
  var actorIndex: Map[WorkerCoordinates, ActorRef] = Map.empty[WorkerCoordinates, ActorRef]
  // required to handle Terminate(deadActor)
  var reversedActorIndex: Map[ActorRef, WorkerCoordinates] = Map.empty[ActorRef, WorkerCoordinates]

  // (d) this index helps locate worker coordinates given a project name. It is not a CRDT assuming
  // all nodes know all projections and use the same tag names. This is filled when projection
  // drivers register the projection (which happens even before ClusterDistribution is started in
  // the local node).
  var nameIndex: Map[ProjectionName, Set[WorkerCoordinates]] = Map.empty[ProjectionName, Set[WorkerCoordinates]]

  // (e) Users may request a status before the projection was registered, in that case, we stash
  // the request in this map.
  var unknownProjections: Map[ProjectionName, Status] = Map.empty[ProjectionName, Status]

  val DefaultRequestedStatus: Status = projectionConfig.defaultRequestedStatus

  override def receive: Receive = {
    case ReportForDuty(coordinates) =>
      log.debug(s"Registering worker $coordinates to [${sender().path.toString}]")
      // keep track
      actorIndex = actorIndex.updated(coordinates, sender())
      reversedActorIndex = reversedActorIndex.updated(sender, coordinates)
      // when worker registers, we must reply with the requested status (if it's been set already, or DefaultInitialStatus if not).
      val initialStatus = requestedStatusLocalCopy.getOrElse(coordinates, DefaultRequestedStatus)
      log.debug(s"Setting initial status [$initialStatus] on worker $coordinates [${sender().path.toString}]")
      sender ! initialStatus

      // watch
      context.watch(sender)

    case GetState =>
      sender ! State.fromReplicatedData(
        nameIndex,
        requestedStatusLocalCopy,
        observedStatusLocalCopy,
        DefaultRequestedStatus,
        Stopped // unless observed somewhere (and replicated), we consider a worker stopped.
      )

    case RegisterProjection(projectionName, tagNames) =>
      log.debug(s"Registering projection $projectionName for tags $tagNames.")
      nameIndex += (projectionName -> tagNames.map {
        WorkerCoordinates(projectionName, _)
      })
      // If we have stashed requestsfor this projection name, unstash them:
      unknownProjections.get(projectionName).foreach { requestedStatus =>
        self ! ProjectionRequestCommand(projectionName, requestedStatus)
        unknownProjections -= projectionName
      }

    // XyzRequestCommand's come from `ProjectionRegistry` and contain a requested Status
    case command: WorkerRequestCommand =>
      log.debug(s"Propagating request $command.")
      updateStateChangeRequests(command.coordinates, command.requestedStatus)
    case command: ProjectionRequestCommand =>
      log.debug(s"Propagating request $command.")
      val projectionWorkers: Option[Set[WorkerCoordinates]] = nameIndex.get(command.projectionName)
      projectionWorkers match {
        case Some(workerSet) =>
          workerSet.foreach(coordinates => updateStateChangeRequests(coordinates, command.requestedStatus))
        case None => unknownProjections += (command.projectionName -> command.requestedStatus)
      }

    // Bare Status come from worker and contain an observed Status
    case observedStatus: Status =>
      log.debug(s"Observed [${sender().path.toString}] as $observedStatus.")
      reversedActorIndex.get(sender()) match {
        case Some(workerName) => updateObservedStates(workerName, observedStatus)
        case None             => log.error(s"Unknown actor [${sender().path.toString}] reports status $observedStatus.")
      }

    case UpdateSuccess(_, _) => //noop: the update op worked nicely, nothing to see here

    // There's three types of UpdateFailure and 3 target CRDTs totalling 9 possible cases of
    // which only UpdateTimeout(_,_) is relevant.
    // case UpdateTimeout(ObservedStatusDataKey, _) =>
    //    the observed status changes very rarely, but when it changes it may change multiple times in a short
    //    period. The fast/often changes probably happen on a cluster rollup, up/down-scale, etc... In any case,
    //    data eventually will become stable (unchanging)in which case data is eventually gossiped and last writer wins.
    // case UpdateTimeout(RequestedStatusDataKey, _) =>
    //    the request status changes very rarely. It is safe to ignore timeouts when using WriteMajority because
    //    data is eventually gossiped.
    // case UpdateTimeout(NameIndexDataKey, _) =>
    //    the data in the nameIndex is only-grow until reaching a full hardcoded representation. It is safe to
    //    ignore timeouts when using WriteMajority because data is eventually gossiped.
    // In any other UpdateFailure cases, noop:
    // - ModifyFailure: using LWWMap with `put` as the modify operation will never fail, latest wins.
    // - StoreFailure: doesn't apply because we don't use durable CRDTs
    case _: UpdateFailure[_] =>
    // Changed is not sent for every single change but, instead, it is batched on the replicator. This means
    //  multiple changes will be notified at once. This is especially relevant when joining a cluster where
    //  instead of getting an avalanche of Changed messages with all the history of the CRDT only a single
    //  message with the latest state is received.
    case changed @ Changed(RequestedStatusDataKey) => {
      val replicatedEntries                       = changed.get(RequestedStatusDataKey).entries
      val diffs: Set[(WorkerCoordinates, Status)] = replicatedEntries.toSet.diff(requestedStatusLocalCopy.toSet)

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

      requestedStatusLocalCopy = replicatedEntries
    }

    case changed @ Changed(ObservedStatusDataKey) =>
      observedStatusLocalCopy = changed.get(ObservedStatusDataKey).entries

    case Terminated(deadActor) =>
      log.debug(s"Worker ${deadActor.path.name} died. Marking it as Stopped.")
      // when a watched actor dies, we mark it as stopped. It will eventually
      // respawn (thanks to EnsureActive) and come back to it's requested status.
      reversedActorIndex.get(deadActor).foreach { coordinates =>
        updateObservedStates(coordinates, Stopped)
      }
      // ... and then update indices and stop watching
      actorIndex = actorIndex - reversedActorIndex(deadActor)
      reversedActorIndex = reversedActorIndex - deadActor
  }

  private def updateStateChangeRequests(coordinates: WorkerCoordinates, requested: Status): Unit = {
    replicator ! Update(RequestedStatusDataKey, LWWMap.empty[WorkerCoordinates, Status], writeConsistency)(
      _.:+(coordinates -> requested)
    )
  }

  private def updateObservedStates(coordinates: WorkerCoordinates, status: Status): Unit = {
    replicator ! Update(ObservedStatusDataKey, LWWMap.empty[WorkerCoordinates, Status], writeConsistency)(
      _.:+(coordinates -> status)
    )
  }
}
