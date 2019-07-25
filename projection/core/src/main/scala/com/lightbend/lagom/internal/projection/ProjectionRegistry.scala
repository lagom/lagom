/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.annotation.ApiMayChange
import akka.pattern.ask
import akka.cluster.sharding.ClusterShardingSettings
import akka.util.Timeout
import com.lightbend.lagom.internal.cluster.ClusterDistribution
import com.lightbend.lagom.internal.cluster.ClusterDistributionSettings
import com.lightbend.lagom.internal.projection.ProjectionRegistry.StateRequestCommand
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.GetState
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.Projection
import com.lightbend.lagom.projection.ProjectionNotFound
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@ApiMayChange
object ProjectionRegistry {

  case class StateRequestCommand(coordinates: WorkerCoordinates, requestedStatus: Status)
}

@ApiMayChange
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

    clusterShardingSettings.withRole(runInRole)

    clusterDistribution.start(
      projectionName,
      WorkerHolderActor.props(projectionName, projectionWorkerPropsFactory, projectionRegistryRef),
      shardNames,
      ClusterDistributionSettings(system).copy(clusterShardingSettings = clusterShardingSettings)
    )

  }

  implicit val exCtx: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout        = Timeout(1.seconds)

  def startWorker(coordinates: WorkerCoordinates): Unit =
    projectionRegistryRef ! StateRequestCommand(coordinates, Started)

  def stopWorker(coordinates: WorkerCoordinates): Unit =
    projectionRegistryRef ! StateRequestCommand(coordinates, Stopped)

  def stopAllWorkers(projectionName: String): Unit = bulk(projectionName, stopWorker)

  def startAllWorkers(projectionName: String): Unit = bulk(projectionName, startWorker)

  private[lagom] def getState(): Future[State] = {
    (projectionRegistryRef ? GetState).mapTo[State]
  }

  private def bulk(projectionName: String, op: WorkerCoordinates => Unit): Unit = {
    val eventualProjection: Future[Projection] = getState()
      .map { state =>
        state.findProjection(projectionName) match {
          case None             => throw ProjectionNotFound(projectionName)
          case Some(projection) => projection
        }
      }
    eventualProjection.map { projection =>
      projection.workers.map(_.tagName).foreach(tagName => op(WorkerCoordinates(projectionName, tagName)))
    }
  }

}
