/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster.projections

import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistryActor.DesiredState
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry

import scala.concurrent.Future

/** TODO: docs
 *
 */
@ApiMayChange
class Projections(private val registry:ProjectionRegistry) {

  def getStatus: Future[DesiredState] =
    registry.getStatus()

  // TODO: impl stop
  // TODO: impl start

}
