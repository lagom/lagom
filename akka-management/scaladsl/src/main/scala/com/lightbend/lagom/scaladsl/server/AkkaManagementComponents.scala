/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.server

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import com.lightbend.lagom.internal.akka.management.AkkaManagementTrigger
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

trait AkkaManagementComponents {

  def configuration: play.api.Configuration
  def actorSystem: ActorSystem
  def coordinatedShutdown: CoordinatedShutdown

  def executionContext: ExecutionContext

  // eager initialization
  val akkaManagementTrigger: AkkaManagementTrigger = {
    val instance = new AkkaManagementTrigger(configuration.underlying, actorSystem, coordinatedShutdown)(executionContext)
    instance.conditionalStart()
    instance
  }

}
