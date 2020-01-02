/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.akka.management

import akka.Done
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.actor.ExtendedActorSystem
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.Config
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * This class works as an entry point for Lagom to start AkkaManagement and register CoordinaterShutdown task for it.
 *
 * Lagom will by default start it in production, but a user can disable it with `lagom.akka.management.enabled = false`.
 *
 * This class will also be used by Lagom's ClusterBootstrap. In which case, AkkaManagement.start may be called twice. Once by
 * Lagom itself, when `lagom.akka.management.enabled = true` (default), and once by Lagom's ClusterBootstrap. This is safe because
 * AkkaManagement.start is idempotent. This also explain why we don't keep any state in this class.
 */
private[lagom] class AkkaManagementTrigger(
    config: Config,
    system: ActorSystem,
    coordinatedShutdown: CoordinatedShutdown
)(implicit executionContext: ExecutionContext) {

  private val enabledSetting       = "lagom.akka.management.enabled"
  private val enabledRenderedValue = config.getValue(enabledSetting).render()
  private val enabled              = config.getBoolean(enabledSetting)

  private val logger = Logger(this.getClass)

  /**
   * Starts Akka HTTP Management honoring the `lagom.akka.management.enabled` setting
   */
  private[lagom] def start() = {

    if (enabled) {
      doStart()
    } else {
      logger.debug(
        s"'lagom.akka.management.enabled' property is set to '$enabledRenderedValue'. Akka Management won't start."
      )
      Future.successful(Done)
    }

  }

  /**
   * Starts Akka HTTP Management ignoring the `lagom.akka.management.enabled` setting.
   */
  private[lagom] def forcedStart(requester: String): Future[Done] = {

    if (!enabled) {
      logger.warn(
        s"'lagom.akka.management.enabled' property is set to '$enabledRenderedValue', " +
          s"but Akka Management is being required to start by: '$requester'."
      )
    }

    doStart()
  }

  private def doStart(): Future[Done] = {

    val akkaManagement = AkkaManagement(system.asInstanceOf[ExtendedActorSystem])
    akkaManagement.start().map { _ =>
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
