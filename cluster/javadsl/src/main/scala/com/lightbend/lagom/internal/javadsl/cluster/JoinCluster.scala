/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.cluster

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.akka.management.AkkaManagementTrigger
import com.lightbend.lagom.internal.cluster.JoinClusterImpl
import com.typesafe.config.Config
import javax.inject.Inject
import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

class JoinClusterModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JoinCluster].toSelf.eagerly()
  )
}

private[lagom] class JoinCluster @Inject() (
  system:                ActorSystem,
  environment:           Environment,
  config:                Config,
  akkaManagementTrigger: AkkaManagementTrigger
) {

  JoinClusterImpl.join(system, environment, config, akkaManagementTrigger)

}
