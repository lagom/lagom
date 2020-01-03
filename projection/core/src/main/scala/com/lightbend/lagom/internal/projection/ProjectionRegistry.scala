/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import akka.pattern.ask
import akka.cluster.sharding.ClusterShardingSettings
import akka.util.Timeout
import com.lightbend.lagom.internal.cluster.ClusterDistribution
import com.lightbend.lagom.internal.cluster.ClusterDistributionSettings
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.GetState
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.Stopped

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Entry point for projection driver classes to create a group of workers and get them distributed
 * across the cluster. When a Driver (e.g. {{{ReadSideImpl}}}) is registering the projection it'll use
 * this class to provide a {{{Props}}} factory, set the tags to track, etc...
 *
 * Internally the {{{ProjectionRegistry}}} uses {{{ClusterDistribution}}} and the {{{ProjectionRegistryActor}}}.
 *
 * @param system
 */
@ApiMayChange
@InternalApi
private[lagom] class ProjectionRegistry(system: ActorSystem) {
  private val projectionRegistryRef: ActorRef = system.actorOf(ProjectionRegistryActor.props, "projection-registry")
  private lazy val clusterShardingSettings    = ClusterShardingSettings(system)
  private lazy val clusterDistribution        = ClusterDistribution(system)

  /**
   *
   * @param projectionName unique name identifying the projection group
   * @param shardNames collection of partition names in the consumed stream
   * @param projectionWorkerPropsFactory
   * @param runInRole
   */
  private[lagom] def registerProjection(
      projectionName: String,
      shardNames: Set[String],
      projectionWorkerPropsFactory: WorkerCoordinates => Props,
      runInRole: Option[String] = None
  ): Unit = {
    projectionRegistryRef ! ProjectionRegistryActor.RegisterProjection(projectionName, shardNames)

    clusterShardingSettings.withRole(runInRole)

    clusterDistribution.start(
      projectionName,
      WorkerCoordinator.props(
        projectionName,
        WorkerConfig(system.settings.config),
        projectionWorkerPropsFactory,
        projectionRegistryRef
      ),
      shardNames,
      ClusterDistributionSettings(system).copy(clusterShardingSettings = clusterShardingSettings)
    )
  }

  implicit val exCtx: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout        = Timeout(1.seconds)

  /** See {{{Projections}}} */
  private[lagom] def stopWorker(coordinates: WorkerCoordinates): Unit =
    projectionRegistryRef ! ProjectionRegistryActor.WorkerRequestCommand(coordinates, Stopped)

  /** See {{{Projections}}} */
  private[lagom] def startWorker(coordinates: WorkerCoordinates): Unit =
    projectionRegistryRef ! ProjectionRegistryActor.WorkerRequestCommand(coordinates, Started)

  /** See {{{Projections}}} */
  private[lagom] def stopAllWorkers(projectionName: String): Unit =
    projectionRegistryRef ! ProjectionRegistryActor.ProjectionRequestCommand(projectionName, Stopped)

  /** See {{{Projections}}} */
  private[lagom] def startAllWorkers(projectionName: String): Unit =
    projectionRegistryRef ! ProjectionRegistryActor.ProjectionRequestCommand(projectionName, Started)

  /** See {{{Projections}}} */
  private[lagom] def getState(): Future[State] = {
    (projectionRegistryRef ? GetState).mapTo[State]
  }
}
