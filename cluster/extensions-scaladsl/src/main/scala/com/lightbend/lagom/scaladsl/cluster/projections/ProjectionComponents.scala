/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster.projections

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents

/**
 *
 */
@ApiMayChange
trait ProjectionComponents extends ClusterComponents {
  def actorSystem: ActorSystem

  private[lagom] lazy val projectionRegistry: ProjectionRegistry = new ProjectionRegistry(actorSystem)
  lazy val projections: Projections                              = new Projections(projectionRegistry)

}
