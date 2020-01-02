/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import scala.concurrent.duration.Duration
import akka.annotation.InternalApi
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

@InternalApi
sealed trait ProjectionConfig {
  def writeMajorityTimeout: FiniteDuration
  def defaultRequestedStatus: Status
}
@InternalApi
object ProjectionConfig {
  def apply(config: Config): ProjectionConfig = {
    new ProjectionConfigImpl(config.getConfig("lagom.projection"))
  }

  private final class ProjectionConfigImpl(config: Config) extends ProjectionConfig {
    val writeMajorityTimeout: FiniteDuration =
      config.getDuration("write.majority.timeout", TimeUnit.MILLISECONDS).millis

    val defaultRequestedStatus: Status = {
      val autoStartEnabled = config.getBoolean("auto-start.enabled")
      if (autoStartEnabled) Started
      else Stopped
    }
  }
}
