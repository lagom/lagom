/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster.projections

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents

/**
 *
 */
@ApiMayChange
trait ProjectorComponents extends ClusterComponents {
  def actorSystem: ActorSystem

  private[lagom] lazy val projectorRegistry: ProjectorRegistry = new ProjectorRegistry(actorSystem)
  lazy val projections: Projections = new Projections(projectorRegistry)

}
