/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.lightbend.lagom.internal.cluster.JoinClusterImpl
import com.lightbend.lagom.scaladsl.playjson.RequiresJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.AkkaManagementComponents
import play.api.Environment

/**
 * Cluster components (for compile-time injection).
 */
trait ClusterComponents extends RequiresJsonSerializerRegistry with AkkaManagementComponents {
  def actorSystem: ActorSystem
  def environment: Environment

  // eager initialization
  val cluster: Cluster = {
    JoinClusterImpl.join(actorSystem, environment, config, akkaManagementTrigger)
    Cluster(actorSystem)
  }
}
