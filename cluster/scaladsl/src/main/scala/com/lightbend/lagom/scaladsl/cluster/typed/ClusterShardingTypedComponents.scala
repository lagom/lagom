/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster.typed

import akka.actor.{ ActorSystem => ActorSystemClassic }
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

/**
 * Akka Cluster Sharding Typed components (for compile-time injection).
 */
trait ClusterShardingTypedComponents {
  def actorSystem: ActorSystemClassic

  lazy val clusterSharding: ClusterSharding = {
    val actorSystemTyped: ActorSystem[_] = {
      import akka.actor.typed.scaladsl.adapter._
      actorSystem.toTyped
    }
    ClusterSharding(actorSystemTyped)
  }
}
