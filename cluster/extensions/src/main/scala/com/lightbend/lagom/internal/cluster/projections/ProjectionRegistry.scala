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
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry._
import com.lightbend.lagom.internal.cluster.ClusterDistribution
import com.lightbend.lagom.internal.cluster.ClusterDistributionSettings
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistryActor.DesiredState
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistryActor.GetDesiredState

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@ApiMayChange
object ProjectionRegistry {

  sealed trait WorkerStatus
  case object Stopped extends WorkerStatus
  case object Started extends WorkerStatus

  sealed trait StateRequest
  case object Stop extends StateRequest
  case object Start extends StateRequest

  @ApiMayChange
  case class ProjectionWorker(name: String, status: WorkerzStatus)

  @ApiMayChange
  case class Projection(name: String, workers: Seq[ProjectionWorker])

}

@ApiMayChange
private[lagom] class ProjectionRegistry(system: ActorSystem) {

  // A ProjectionRegistry is responsible for this node's ProjectionRegistryActor instance.
  // TODO: decide what to do if/when the ProjectionRegistryActor dies (note the loss of references to projections).
  private val projectionRegistryRef: ActorRef = system.actorOf(ProjectionRegistryActor.props, "projection-registry")
  private lazy val clusterShardingSettings   = ClusterShardingSettings(system)
  private lazy val clusterDistribution       = ClusterDistribution(system)

  /**
   *
   * @param streamName name of the stream this projection group consumes
   * @param shardNames collection of partition names in the consumed stream
   * @param projectionName unique name identifying the projection group
   * @param runInRole
   * @param projectionPropsFactory
   */
  private[lagom] def registerProjectionGroup(
      streamName: String,
      shardNames: Set[String],
      projectionName: String,
      runInRole: Option[String],
      projectionPropsFactory: ActorRef => Props
  ): Unit = {

    clusterShardingSettings.withRole(runInRole)

    clusterDistribution.start(
      projectionName,
      projectionPropsFactory(projectionRegistryRef),
      shardNames,
      ClusterDistributionSettings(system).copy(clusterShardingSettings = clusterShardingSettings)
    )

  }

  private[lagom] def getStatus(): Future[DesiredState] = {
    implicit val exCtx: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout        = Timeout(1.seconds)
    (projectionRegistryRef ? GetDesiredState).mapTo[DesiredState]
  }
}
