/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.projections

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.cluster.sharding.ClusterShardingSettings
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.projections.ProjectorRegistry._
import com.lightbend.lagom.internal.persistence.projections.ProjectorRegistryActor._
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistributionSettings

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

object ProjectorRegistry {
  sealed trait ProjectorStatus
  case object Stopped  extends ProjectorStatus
  case object Starting extends ProjectorStatus
  case object Running  extends ProjectorStatus
  case object Stopping extends ProjectorStatus

  case class ProjectionMetadata(streamName: String, projectorName: String, tagName: Option[String] = None)

}

// TODO: create and manage from DI.
class ProjectorRegistry(system: ActorSystem) {

  // A ProjectorRegistry is responsible for this node's ProjectorRegistryActor instance.
  // TODO: decide what to do if/when the ProjectorRegistryActor dies (note the loss of references to projectors).
  private val projectorRegistryRef: ActorRef = system.actorOf(ProjectorRegistryActor.props, "projector-registry")
  private lazy val clusterShardingSettings        = ClusterShardingSettings(system)
  private lazy val clusterDistribution            = ClusterDistribution(system)

  private[lagom] def register(
      // We could replace the `streamName` argument with the Persistent Entity type and extract the name from that
      // but that introduces some coupling we should avoid.
      streamName: String,
      shardNames: Set[String],
      projectorName: String,
      runInRole: Option[String],
      projectorPropsFactory: ActorRef => Props
  ): Unit = {

    clusterShardingSettings.withRole(runInRole)

    clusterDistribution.start(
      projectorName,
      projectorPropsFactory(projectorRegistryRef),
      shardNames,
      ClusterDistributionSettings(system).copy(clusterShardingSettings = clusterShardingSettings)
    )

  }

  def getStatus(): Future[Map[ProjectionMetadata, ProjectorStatus]] = {
    implicit val exCtx: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout        = Timeout(1.seconds)
    (projectorRegistryRef ? GetStatus).mapTo[Map[ProjectionMetadata, ProjectorStatus]]
  }
}
