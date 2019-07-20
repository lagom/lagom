/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.Stopped

object WorkerHolderActor {
  def props(
      projectionName: String,
      workerProps: String => Props,
      projectionRegistryActorRef: ActorRef
  ): Props = Props(
    new WorkerHolderActor(
      projectionName,
      workerProps,
      projectionRegistryActorRef
    )
  )

}

class WorkerHolderActor(projectionName: String, workerProps: String => Props, projectionRegistryActorRef: ActorRef)
    extends Actor
    with ActorLogging {

  override def receive = unidentified

  private def unidentified: Receive = {
    case EnsureActive(workerName) =>
      log.debug(s"Requesting registry of $workerName [${self.path.toString}].")
      projectionRegistryActorRef ! ProjectionRegistryActor.RegisterProjection(projectionName, workerName)
      // default observed status is Stopped
      doStop(workerName)
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

  // TODO: when child dies, restart it. Don't report it as stopped
  private def started(workerName: String): Receive = {
    case EnsureActive(_) => // yes, we're active
    case Started         => // yes, we're started
    case Stopped         => doStop(workerName)
  }

  private def stopped(workerName: String): Receive = {
    case EnsureActive(_) => // yes, we're active
    case Stopped         => // yes, we're stopped
    case Started         => doStart(workerName)
  }

  private def doStop(workerName: String): Unit = {
    log.debug("Setting self as Stopped.")
    context.children.foreach(context.stop)
    projectionRegistryActorRef ! Stopped
    context.become(stopped(workerName))
  }

  private def doStart(workerName: String): Unit = {
    log.debug("Setting self as Started.")
    context.system.actorOf(workerProps(workerName))
    projectionRegistryActorRef ! Started
    context.become(started(workerName))
  }

}
