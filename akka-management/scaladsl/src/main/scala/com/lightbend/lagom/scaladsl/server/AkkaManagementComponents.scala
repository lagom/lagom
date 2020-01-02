/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.server

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import com.lightbend.lagom.internal.akka.management.AkkaManagementTrigger
import play.api.Environment
import play.api.Mode

import scala.concurrent.ExecutionContext

trait AkkaManagementComponents {
  def configuration: play.api.Configuration
  def actorSystem: ActorSystem
  def coordinatedShutdown: CoordinatedShutdown
  def environment: Environment

  def executionContext: ExecutionContext

  // eager initialization
  private[lagom] val akkaManagementTrigger: AkkaManagementTrigger = {
    val instance =
      new AkkaManagementTrigger(configuration.underlying, actorSystem, coordinatedShutdown)(executionContext)
    if (environment.mode == Mode.Prod) {
      instance.start()
    }
    instance
  }
}
