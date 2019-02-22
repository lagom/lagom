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
    if (environment.mode == Mode.Prod) {
      Seq(bind[AkkaManagementTrigger].toProvider[AkkaManagementProvider].eagerly())
    } else {
      Seq.empty[Binding[_]]
    }
  }
}

@Singleton
private[lagom] class AkkaManagementProvider @Inject() (
  config:              Config,
  actorSystem:         ActorSystem,
  coordinatedShutdown: CoordinatedShutdown,
  executionContext:    ExecutionContext
)
  extends Provider[AkkaManagementTrigger] {

  override def get(): AkkaManagementTrigger = {
    val managementTrigger = new AkkaManagementTrigger(config, actorSystem, coordinatedShutdown)(executionContext)
    managementTrigger.conditionalStart()
    managementTrigger
  }

}

