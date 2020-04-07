/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.projection

import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.State

import scala.concurrent.Future

/**
 * Public interface to query the internal projection registry that keeps track of the {{{State}}} of each projection.
 * A projections is a process consuming an Akka Persistence Journal handling each event into a read table of
 * a broker topic. In Lagom the erm projection only refers to `ReadSideProcessor`'s  and `TopicProducers`'s
 * (not Broker subscribers). This is different than the meaning of projection in akka/akka-projection.
 *
 * See also https://www.lagomframework.com/documentation/latest/scala/Projections.html
 */
@ApiMayChange
final class Projections(private val registry: ProjectionRegistry) {

  /**
   * Read an eventually consistent copy of the projections and workers running on the service.
   * Invocations to this method return a local copy of the {{{State}}} so multiple invocations in
   * different nodes of the cluster may return different versions of {{{State}}} when it is evolving
   * (e.g. during a rolling upgrade, while attending a user request to stop/start,...)
   * @return an eventually consistent list of projections' metadata
   */
  def getStatus: Future[State] =
    registry.getState()

  /**
   * Given a `projectionName`, request all its workers to stop. This method returns immediately.
   * Eventually, the request will propagate across the cluster and, after that, workers participating
   * on that projection will stop. To track the actual state of the workers, poll the projection
   * registry using the method {{{Projections#getStatus}}}.
   */
  def stopAllWorkers(projectionName: String): Unit =
    registry.stopAllWorkers(projectionName)

  /**
   * Given a `projectionName` and a `tagName` request a single worker to stop. This method returns immediately.
   * Eventually, the request will propagate across the cluster and reach the node where that particular
   * worker is currently allocated. Then, the worker will be stopped, it's status change will be observed
   * and propagated back across the cluster. To track the actual state of the workers, poll the projection
   * registry using the method `getStatus`.
   */
  def stopWorker(projectionName: String, tagName: String): Unit =
    registry.stopWorker(WorkerCoordinates(projectionName, tagName))

  /**
   * Given a `projectionName`, request all its workers to start. This method returns immediately.
   * Eventually, the request will propagate across the cluster and, after that, workers participating
   * on that projection will start. To track the actual state of the workers, poll the projection
   * registry using the method `getStatus`.
   */
  def startAllWorkers(projectionName: String): Unit =
    registry.startAllWorkers(projectionName)

  /**
   * Given a `projectionName` and a `tagName` request a single worker to start. This method returns immediately.
   * Eventually, the request will propagate across the cluster and reach the node where that particular
   * worker must be allocated allocated. Then, the worker will be started, it's status change will be observed
   * and propagated back across the cluster. To track the actual state of the workers, poll the projection
   * registry using the method `getStatus`.
   */
  def startWorker(projectionName: String, tagName: String): Unit =
    registry.startWorker(WorkerCoordinates(projectionName, tagName))
}
