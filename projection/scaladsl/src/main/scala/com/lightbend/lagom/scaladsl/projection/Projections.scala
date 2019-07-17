/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.projection

import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.DesiredState
import com.lightbend.lagom.internal.projection.ProjectionRegistry

import scala.concurrent.Future

// https://github.com/lagom/lagom/issues/2048
/** TODO: docs
 *
 */
@ApiMayChange
class Projections(private val registry: ProjectionRegistry) {

  def getStatus: Future[DesiredState] =
    registry.getStatus()

  def stopAllWorkers(projectionName: String) =
    registry.stopAllWorkers(projectionName)
  def stopWorker(projectionWorkerName: String) =
    registry.stopWorker(projectionWorkerName)

}
