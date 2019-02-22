/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.server

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import com.lightbend.lagom.internal.akka.management.AkkaManagementTrigger
import play.api.{ Environment, Mode }

import scala.concurrent.ExecutionContext

trait AkkaManagementComponents {

  def configuration: play.api.Configuration
  def actorSystem: ActorSystem
  def coordinatedShutdown: CoordinatedShutdown
  def environment: Environment

  def executionContext: ExecutionContext

  // eager initialization
  private[lagom] val akkaManagementTrigger: AkkaManagementTrigger = {
    val instance = new AkkaManagementTrigger(configuration.underlying, actorSystem, coordinatedShutdown)(executionContext)
    if (environment.mode == Mode.Prod) {
      instance.start()
    }
    instance
  }

}
