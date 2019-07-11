/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster.projections

import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryActor.DesiredStatus
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry

import scala.concurrent.Future

/** TODO: docs
 *
 */
@ApiMayChange
class Projections(private val impl:ProjectorRegistry) {

  def getStatus: Future[DesiredStatus] =
    impl.getStatus()

  // TODO: impl stop
  // TODO: impl start

}
