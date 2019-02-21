/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.akka.management

import akka.Done
import akka.actor.{ ActorSystem, CoordinatedShutdown, ExtendedActorSystem }
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }

private[lagom] class AkkaManagementTrigger(
  config:              Config,
  system:              ActorSystem,
  coordinatedShutdown: CoordinatedShutdown
)(implicit executionContext: ExecutionContext) {

  private val enabled = config.getBoolean("lagom.akka.management.enabled")

  /**
   * Starts Akka HTTP Management honoring the `lagom.akka.management.enabled` setting
   */
  private[lagom] def conditionalStart() = {

    if (enabled) {
      forcedStart()
    } else {
      Future.successful(Done)
    }

  }

  /**
   * Starts Akka HTTP Management ignoring the `lagom.akka.management.enabled` setting.
   */
  private[lagom] def forcedStart(): Future[Done] = {
    val akkaManagement = AkkaManagement(system.asInstanceOf[ExtendedActorSystem])
    akkaManagement.start().map {
      _ =>
        // add a task to stop
        coordinatedShutdown.addTask(
          CoordinatedShutdown.PhaseBeforeServiceUnbind,
          "stop-akka-http-management"
        ) { () =>
            akkaManagement.stop()
          }
        Done
    }

  }
}