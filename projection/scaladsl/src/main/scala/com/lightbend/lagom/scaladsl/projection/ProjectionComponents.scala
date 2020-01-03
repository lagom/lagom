/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.projection

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents

/**
 * Provides an instance of {{{Projections}}} to interact with the (internal) {{{ProjectionRegistry}}}.
 */
@ApiMayChange
trait ProjectionComponents extends ClusterComponents {
  def actorSystem: ActorSystem

  private[lagom] lazy val projectionRegistry: ProjectionRegistry = new ProjectionRegistry(actorSystem)
  lazy val projections: Projections                              = new Projections(projectionRegistry)
}
