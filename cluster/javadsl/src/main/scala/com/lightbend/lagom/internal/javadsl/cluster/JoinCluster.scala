/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.cluster

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.akka.management.AkkaManagementTrigger
import com.lightbend.lagom.internal.cluster.JoinClusterImpl
import javax.inject.Inject
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

class JoinClusterModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JoinCluster].toSelf.eagerly()
  )
}

private[lagom] class JoinCluster @Inject() (
    system: ActorSystem,
    environment: Environment,
    akkaManagementTrigger: AkkaManagementTrigger
) {
  JoinClusterImpl.join(system, environment, akkaManagementTrigger)
}
