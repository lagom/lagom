/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.pattern.BackoffOpts
import akka.pattern.BackoffSupervisor
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped

import scala.concurrent.duration._

object WorkerCoordinator {
  def props(
      projectionName: String,
      workerConfig: WorkerConfig,
      workerProps: WorkerCoordinates => Props,
      projectionRegistryActorRef: ActorRef
  ): Props = {
    val backOffChildProps: WorkerCoordinates => Props = coordinates =>
      BackoffSupervisor.props(
        BackoffOpts.onFailure(
          workerProps(coordinates),
          childName = coordinates.workerActorName,
          minBackoff = workerConfig.minBackoff,
          maxBackoff = workerConfig.maxBackoff,
          randomFactor = workerConfig.randomFactor
        )
      )

    /*
     * <pre>
     * WorkerHolder uses names provided by Akka Cluster sharding (we don't control that name)
     *   +-- BackOffActor (named `coordinates.backoffActorName` as set on `doStart` below)
     *      +-- actual worker Actor (named `coordinates.workerActorName` as set on in lines above)
     * </pre>
     */

    Props(
      new WorkerCoordinator(
        projectionName,
        backOffChildProps,
        projectionRegistryActorRef
      )
    )
  }
}

/**
 * The WorkerCoordinator actor spawns as soon as it receives {{{EnsureActive(tagName)}}} messages from the
 * cluster and remains alive forever. Even when the requested status for given {{{WorkerCoordinates}}} dictate
 * the status to be {{{Stopped}}} the {{{WorkerCoordinator}}} will exist. When getting the first EnsureActive
 * message, the WorkerCoordinator pings back to the {{{ProjectionRegistryActor}}} to indicate “I’m here, I represent a
 * worker for this WorkerCoordinates”. That is, when the cluster starts up there are as many instances of
 * {{{WorkerCoordinator}}} as (numProjections x numTags). The same is not true for the actual worker actors running
 * the queryByTag streams. The actual worker actors are only started when requested and are stopped when requested.
 *
 * An advantage of this pattern is that, because a WorkerCoordinator actor will know the projectionName
 * and the tagName before the actual worker actor is created we can now make up a unique repeatable name
 * for the worker actor. This unique String is called the workerKey. The workerKey is used in the worker actor name.
 * This is useful in monitoring since the information regarding projection and tagName is easily accessible, but also
 * because the ActorSystem can't run two actors with the same name. Trying to spawn the same worker in a single node
 * will cause a failure.
 *
 * See https://github.com/playframework/play-meta/blob/master/docs/design/projections-design.md#workercoordinator-actor
 *
 * @param projectionName
 * @param workerProps
 * @param projectionRegistryActorRef
 */
class WorkerCoordinator(
    projectionName: String,
    workerProps: WorkerCoordinates => Props,
    projectionRegistryActorRef: ActorRef
) extends Actor
    with ActorLogging {
  override def receive: Receive = unidentified

  var lastStashed: Option[Status] = None

  private def unidentified: Receive = {
    case EnsureActive(tagName) =>
      val coordinates = WorkerCoordinates(projectionName, tagName)
      log.debug(s"Requesting registry of $coordinates [${self.path.toString}].")
      projectionRegistryActorRef ! ProjectionRegistryActor.ReportForDuty(coordinates)
      // become stopped and await for instructions from the Registry
      becomeStopped(coordinates)
  }

  // the response to a RegisterProjection in `projectionRegistryActorRef` should be the
  // requested Status which we will handle here.
  // a) if the requested status has been set and propagated to our node then we'll get the value
  // b) if the requested status is still not set in this node for our workerName then we won't
  //    get anything as a response. But! Anytime a change on the requested status is detected, that
  //    is propagated to the appropriate worker (aka, this) so the requested status will eventually
  //    arrive here.
  // c) TODO:: (potential improvement in case of edge cases not covered in (a) and (b)) we can
  //    even send a new `RegisterProjection` from the `identified` behaviour anytime an
  //    `EnsureActive` arrives. This will force a redelivery of the requested status and will
  //    decouple the implementation of ProjectionRegistryActor from this implementation.

  private def started(coordinates: WorkerCoordinates): Receive = {
    case EnsureActive(_) => // yes, we're active
    case Started         => // yes, we're started
    case Stopped         => doStop(coordinates)
  }

  private def stopped(coordinates: WorkerCoordinates): Receive = {
    case EnsureActive(_) => // yes, we're active
    case Stopped         => // yes, we're stopped
    case Started         => doStart(coordinates)
  }

  private def stopping(coordinates: WorkerCoordinates): Receive = {
    case EnsureActive(_) => // yes, we're active
    case Stopped         => lastStashed = None // we're already stopping
    case Started         => lastStashed = Some(Started)
    case Terminated(_) =>
      becomeStopped(coordinates)
      // During the `stopping` we may get new requested status.
      // `lastStashed` is a poor man's stash
      lastStashed.foreach { st =>
        self ! st
      }
      lastStashed = None
  }

  private def becomeStopped(coordinates: WorkerCoordinates): Unit = {
    projectionRegistryActorRef ! Stopped
    context.become(stopped(coordinates))
  }

  private def doStop(coordinates: WorkerCoordinates): Unit = {
    context.child(coordinates.supervisingActorName) match {
      case Some(child) =>
        log.debug(s"Stopping worker $coordinates...")
        context.watch(child)
        context.stop(child)
        context.become(stopping(coordinates))
      case None =>
        context.become(stopped(coordinates))
    }
  }

  private def becomeStarted(coordinates: WorkerCoordinates): Unit = {
    projectionRegistryActorRef ! Started
    context.become(started(coordinates))
  }

  private def doStart(coordinates: WorkerCoordinates): Unit = {
    log.debug("Setting self as Started.")
    context.actorOf(workerProps(coordinates), name = coordinates.supervisingActorName)
    // https://github.com/lagom/lagom/issues/2131
    // TODO: don't report Started unless underlying is fully started.
    // - When the underlying actor enters a StartupCrashLoop (e.g., the offsetStore is not ready,
    // the backing DB causes an issue,...) we will be reporting the observed state as `Started`
    // but that is not true. Instead, we may need a `starting` phase in this actor (WorkerHolder)
    // and some form of signal from the underlying actor (the worker) to indicate the stream doing
    // the processing is 100% operational. Then, the code below will not directly
    // become(started) but become(starting)
    becomeStarted(coordinates)
  }
}
