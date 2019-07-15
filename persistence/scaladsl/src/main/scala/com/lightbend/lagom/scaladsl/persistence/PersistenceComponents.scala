/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.scaladsl.persistence.ReadSideImpl
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents
import com.lightbend.lagom.scaladsl.cluster.projections.ProjectionComponents
import com.lightbend.lagom.scaladsl.cluster.projections.Projections
import play.api.Configuration

import scala.concurrent.ExecutionContext

/**
 * Persistence components (for compile-time injection).
 */
trait PersistenceComponents extends ReadSidePersistenceComponents

/**
 * Write-side persistence components (for compile-time injection).
 */
trait WriteSidePersistenceComponents extends ClusterComponents {
  def persistentEntityRegistry: PersistentEntityRegistry
}

/**
 * Read-side persistence components (for compile-time injection).
 */
trait ReadSidePersistenceComponents extends WriteSidePersistenceComponents with ProjectionComponents {
  def actorSystem: ActorSystem
  def executionContext: ExecutionContext
  def materializer: Materializer
  private[lagom] def projectionRegistry: ProjectionRegistry

  def configuration: Configuration

  lazy val readSideConfig: ReadSideConfig = ReadSideConfig(
    configuration.underlying.getConfig("lagom.persistence.read-side")
  )
  lazy val readSide: ReadSide =
    new ReadSideImpl(actorSystem, readSideConfig, persistentEntityRegistry, projectionRegistry, None)(
      executionContext,
      materializer
    )

}
