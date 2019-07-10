/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster.projections

import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl.ProjectionMetadata
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl.ProjectorStatus

import scala.concurrent.Future

/** TODO: docs
 *
 */
class ProjectorRegistry(impl:ProjectorRegistryImpl) {

  def getStatus: Future[Map[ProjectionMetadata, ProjectorStatus]] =
    impl.getStatus()

  // TODO: impl stop
  // TODO: impl start

}
