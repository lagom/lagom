/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.cluster

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.lightbend.lagom.internal.cluster.JoinClusterImpl

/**
 * Cluster components (for compile-time injection).
 */
trait ClusterComponents {
  def actorSystem: ActorSystem

  // eager initialization
  val cluster: Cluster = {
    JoinClusterImpl.join(actorSystem)
    Cluster(actorSystem)
  }
}
