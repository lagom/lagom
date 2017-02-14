/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cluster

import akka.Done
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ShardRegion

private[lagom] object GracefulLeave {
  def props(entityTypeNames: Set[String]): Props =
    Props(new GracefulLeave(entityTypeNames))

  case object Leave
  private case object Removed
}

private[lagom] class GracefulLeave(entityTypeNames: Set[String])
  extends Actor {
  import GracefulLeave._
  import context.dispatcher
  val system = context.system
  val cluster = Cluster(system)

  def receive = {
    case Leave ⇒
      if (entityTypeNames.isEmpty) {
        cluster.leave(cluster.selfAddress)
        context.become(leavingInProgress(sender()))
      } else {
        entityTypeNames.foreach { name =>
          val region = ClusterSharding(system).shardRegion(name)
          context.watch(region)
          region ! ShardRegion.GracefulShutdown
        }
        context.become(shardingInProgress(sender(), entityTypeNames.size))
      }
  }

  def shardingInProgress(replyTo: ActorRef, count: Int): Receive = {
    case Terminated(_) ⇒
      if (count == 1) {
        cluster.registerOnMemberRemoved(self ! Removed)
        cluster.leave(cluster.selfAddress)
        context.become(leavingInProgress(replyTo))
      } else
        context.become(shardingInProgress(replyTo, count - 1))
  }

  def leavingInProgress(replyTo: ActorRef): Receive = {
    case Removed =>
      replyTo ! Done
      context.stop(self)
  }
}
