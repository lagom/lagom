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
import com.lightbend.lagom.internal.cluster.ClusterDistribution
import com.lightbend.lagom.internal.cluster.ClusterDistributionSettings
import com.lightbend.lagom.internal.projection.ProjectionRegistry.StateRequestCommand
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.GetState
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

  case class StateRequestCommand(workerName: String, requested: Status)
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

  private[lagom] def getStatus(): Future[State] = {
    (projectionRegistryRef ? GetState).mapTo[State]
  }

  def startWorker(projectionWorkerName: String): Future[Done] =
    (projectionRegistryRef ? StateRequestCommand(projectionWorkerName, Started))
      .mapTo[Done]

  def stopWorker(projectionWorkerName: String): Future[Done] =
    (projectionRegistryRef ? StateRequestCommand(projectionWorkerName, Stopped))
      .mapTo[Done]

  // TODO: untested
  // The way to test this is to write an expectation in MultinodeExpect which
  // groups and shares observed messages accross the cluster
  def stopAllWorkers(projectionName: String): Future[Done] = bulk(projectionName, stopWorker)

  // TODO: untested
  def startAllWorkers(projectionName: String): Future[Done] = bulk(projectionName, startWorker)

  private def bulk(projectionName: String, op: String => Future[Done]): Future[Done] = {
    (projectionRegistryRef ? GetState)
      .mapTo[State]
      .map { state =>
        state.findProjection(projectionName) match {
          case None             => throw ProjectionNotFound(projectionName)
          case Some(projection) => projection
        }
      }
      .map(_.workers.map(worker => op(worker.name)))
      .map(_ => Done)
  }

}
