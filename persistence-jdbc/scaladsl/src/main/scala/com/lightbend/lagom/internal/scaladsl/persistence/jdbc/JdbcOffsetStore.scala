/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.jdbc

import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.jdbc.AbstractSlickOffsetStoreConfiguration
import com.typesafe.config.Config
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

/**
 * INTERNAL API
 */
class OffsetTableConfiguration(config: Config, readSideConfig: ReadSideConfig)
  extends AbstractSlickOffsetStoreConfiguration(config) {

  override def minBackoff: FiniteDuration = readSideConfig.minBackoff
  override def maxBackoff: FiniteDuration = readSideConfig.maxBackoff
  override def randomBackoffFactor: Double = readSideConfig.randomBackoffFactor
  override def globalPrepareTimeout: FiniteDuration = readSideConfig.globalPrepareTimeout
  override def role: Option[String] = readSideConfig.role
  override def toString: String = s"OffsetTableConfiguration($tableName,$schemaName)"
}
