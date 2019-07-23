/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.projection

import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.State

import scala.concurrent.Future

// https://github.com/lagom/lagom/issues/2048
/** TODO: docs
 *
 */
@ApiMayChange
class Projections(private val registry: ProjectionRegistry) {

  def getStatus: Future[State] =
    registry.getState()

  def stopAllWorkers(projectionName: String) =
    registry.stopAllWorkers(projectionName)
  def stopWorker(projectionName: String, tagName: String) =
    registry.stopWorker(WorkerCoordinates(projectionName, tagName))

}
