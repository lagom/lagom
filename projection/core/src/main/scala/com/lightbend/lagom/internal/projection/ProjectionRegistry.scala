/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.Done
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.annotation.ApiMayChange
import akka.pattern.ask
import akka.cluster.sharding.ClusterShardingSettings
import akka.util.Timeout
import com.lightbend.lagom.internal.projection.ProjectionRegistry._
import com.lightbend.lagom.internal.cluster.ClusterDistribution
import com.lightbend.lagom.internal.cluster.ClusterDistributionSettings
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.DesiredState
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.GetDesiredState

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

@ApiMayChange
object ProjectionRegistry {

  sealed trait WorkerStatus
  sealed trait Stopped extends WorkerStatus
  case object Stopped  extends Stopped
  sealed trait Started extends WorkerStatus
  case object Started  extends Started

  sealed trait StateRequest
  case class Stop(workerName: String)  extends StateRequest
  case class Start(workerName: String) extends StateRequest

  @ApiMayChange
  case class ProjectionWorker(name: String, status: WorkerStatus)

  @ApiMayChange
  case class Projection(name: String, workers: Seq[ProjectionWorker])

  @ApiMayChange
  case class ProjectionNotFound(projectionName: String)
      extends RuntimeException(s"Projection $projectionName is not registered")
      with NoStackTrace
  @ApiMayChange
  case class ProjectionWorkerNotFound(workerName: String)
      extends RuntimeException(s"Projection $workerName is not registered")
      with NoStackTrace

}

@ApiMayChange
private[lagom] class ProjectionRegistry(system: ActorSystem) {

  private val projectionRegistryRef: ActorRef = system.actorOf(ProjectionRegistryActor.props, "projection-registry")
  private lazy val clusterShardingSettings    = ClusterShardingSettings(system)
  private lazy val clusterDistribution        = ClusterDistribution(system)

  /**
   *
   * @param streamName name of the stream this projection group consumes
   * @param projectionName unique name identifying the projection group
   * @param shardNames collection of partition names in the consumed stream
   * @param runInRole
   * @param projectionPropsFactory
   */
  private[lagom] def registerProjectionGroup(
      streamName: String,
      projectionName: String,
      shardNames: Set[String],
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

  implicit val exCtx: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout        = Timeout(1.seconds)

  private[lagom] def getStatus(): Future[DesiredState] = {
    (projectionRegistryRef ? GetDesiredState).mapTo[DesiredState]
  }

  def startWorker(projectionWorkerName: String): Future[Done] =
    (projectionRegistryRef ? Start(projectionWorkerName))
      .mapTo[Done]

  def stopWorker(projectionWorkerName: String): Future[Done] =
    (projectionRegistryRef ? Stop(projectionWorkerName))
      .mapTo[Done]

  // TODO: untested
  def stopAllWorkers(projectionName: String): Future[Done] =
    (projectionRegistryRef ? GetDesiredState)
      .mapTo[DesiredState]
      .map { desiredState =>
        desiredState.findProjection(projectionName) match {
          case None             => throw new ProjectionNotFound(projectionName)
          case Some(projection) => projection
        }
      }
      .map(_.workers.map(worker => stopWorker(worker.name)))
      .map(_ => Done)

}
