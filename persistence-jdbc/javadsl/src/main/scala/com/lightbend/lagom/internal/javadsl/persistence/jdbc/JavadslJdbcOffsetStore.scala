/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.{ AbstractSlickOffsetStoreConfiguration, SlickOffsetStore }
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

/**
 * INTERNAL API
 */
@Singleton
private[lagom] class OffsetTableConfiguration @Inject() (config: Configuration, readSideConfig: ReadSideConfig)
  extends AbstractSlickOffsetStoreConfiguration(config) {
  override def minBackoff: FiniteDuration = readSideConfig.minBackoff
  override def maxBackoff: FiniteDuration = readSideConfig.maxBackoff
  override def randomBackoffFactor: Double = readSideConfig.randomBackoffFactor
  override def globalPrepareTimeout: FiniteDuration = readSideConfig.globalPrepareTimeout
  override def role: Option[String] = readSideConfig.role
  override def toString: String = s"OffsetTableConfiguration($tableName,$schemaName)"
}

@Singleton
private[lagom] class JavadslJdbcOffsetStore @Inject() (slick: SlickProvider, system: ActorSystem, tableConfig: OffsetTableConfiguration,
                                                       readSideConfig: ReadSideConfig)(implicit ec: ExecutionContext)
  extends SlickOffsetStore(system, slick, tableConfig)
