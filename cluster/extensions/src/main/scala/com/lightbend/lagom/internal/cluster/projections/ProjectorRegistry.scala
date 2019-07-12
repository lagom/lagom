/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster.projections

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.annotation.ApiMayChange
import akka.pattern.ask
import akka.cluster.sharding.ClusterShardingSettings
import akka.util.Timeout
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry._
import com.lightbend.lagom.internal.cluster.ClusterDistribution
import com.lightbend.lagom.internal.cluster.ClusterDistributionSettings
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryActor.DesiredState
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryActor.GetStatus

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@ApiMayChange
object ProjectorRegistry {
  sealed trait ProjectorStatus
  case object Stopped  extends ProjectorStatus
  case object Starting extends ProjectorStatus
  case object Running  extends ProjectorStatus
  case object Stopping extends ProjectorStatus

  @ApiMayChange
  case class ProjectorWorker(name: String, status: ProjectorStatus)

  @ApiMayChange
  case class Projector(name: String, workers: Seq[ProjectorWorker])

}

@ApiMayChange
private[lagom] class ProjectorRegistry(system: ActorSystem) {

  // A ProjectorRegistry is responsible for this node's ProjectorRegistryActor instance.
  // TODO: decide what to do if/when the ProjectorRegistryActor dies (note the loss of references to projectors).
  private val projectorRegistryRef: ActorRef = system.actorOf(ProjectorRegistryActor.props, "projector-registry")
  private lazy val clusterShardingSettings   = ClusterShardingSettings(system)
  private lazy val clusterDistribution       = ClusterDistribution(system)

  /**
   *
   * @param streamName name of the stream this projector group consumes
   * @param shardNames collection of partition names in the consumed stream
   * @param projectorName unique name identifying the projector group
   * @param runInRole
   * @param projectorPropsFactory
   */
  private[lagom] def registerProjectorGroup(
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

  private[lagom] def getStatus(): Future[DesiredState] = {
    implicit val exCtx: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout        = Timeout(1.seconds)
    (projectorRegistryRef ? GetStatus).mapTo[DesiredState]
  }
}
