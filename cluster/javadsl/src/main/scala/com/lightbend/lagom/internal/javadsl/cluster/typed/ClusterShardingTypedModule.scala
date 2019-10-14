/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.cluster.typed

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import javax.inject.Inject
import javax.inject.Provider
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

class ClusterShardingTypedModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ClusterSharding].toProvider[ClusterShardingTypedProvider]
  )
}

private[lagom] class ClusterShardingTypedProvider @Inject() (system: ActorSystem) extends Provider[ClusterSharding] {
  private val instance: ClusterSharding = {
    import akka.actor.typed.scaladsl.adapter._
    val actorSystemTyped = system.toTyped
    ClusterSharding.get(actorSystemTyped)
  }

  override def get(): ClusterSharding = instance
}
