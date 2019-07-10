/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.cluster.projections

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents

/**
 *
 */
trait ProjectorComponents extends ClusterComponents {
  def actorSystem: ActorSystem

  private[lagom] lazy val projectorRegistryImpl: ProjectorRegistryImpl = new ProjectorRegistryImpl(actorSystem)
  lazy val projectorRegistry: ProjectorRegistry = new ProjectorRegistry(projectorRegistryImpl)

}
