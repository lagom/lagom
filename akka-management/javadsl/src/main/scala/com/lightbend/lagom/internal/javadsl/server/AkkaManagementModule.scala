/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.server

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import com.lightbend.lagom.internal.akka.management.AkkaManagementTrigger
import com.typesafe.config.Config
import javax.inject.{ Inject, Provider, Singleton }
import play.api.inject.{ Binding, Module }
import play.api.{ Configuration, Environment, Mode }

import scala.concurrent.ExecutionContext

private[lagom] class AkkaManagementModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    // The trigger must be eager because it's often not required by anyone as a dependency to
    // be injected and yet it must be started anyway
    Seq(bind[AkkaManagementTrigger].toProvider[AkkaManagementProvider].eagerly())
  }
}

@Singleton
private[lagom] class AkkaManagementProvider @Inject() (
  config:              Config,
  actorSystem:         ActorSystem,
  coordinatedShutdown: CoordinatedShutdown,
  environment:         Environment,
  executionContext:    ExecutionContext
)
  extends Provider[AkkaManagementTrigger] {

  override def get(): AkkaManagementTrigger = {
    val instance = new AkkaManagementTrigger(config, actorSystem, coordinatedShutdown)(executionContext)
    if (environment.mode == Mode.Prod) {
      instance.start()
    }
    instance
  }

}

