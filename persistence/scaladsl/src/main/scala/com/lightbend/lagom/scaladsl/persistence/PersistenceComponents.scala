/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.scaladsl.persistence.ReadSideImpl
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents
import com.typesafe.config.Config
import play.api.Configuration

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
trait ReadSidePersistenceComponents extends WriteSidePersistenceComponents {
  def actorSystem: ActorSystem
  def executionContext: ExecutionContext
  def materializer: Materializer

  @deprecated(message = "prefer `config` using typesafe Config instead", since = "1.4.0")
  def configuration: Configuration

  lazy val readSideConfig: ReadSideConfig = ReadSideConfig(configuration.underlying.getConfig("lagom.persistence.read-side"))
  lazy val readSide: ReadSide = new ReadSideImpl(actorSystem, readSideConfig, persistentEntityRegistry)(
    executionContext, materializer
  )

}
