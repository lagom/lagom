/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.annotation.InternalApi
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

@InternalApi
sealed trait WorkerConfig {
  def minBackoff: FiniteDuration
  def maxBackoff: FiniteDuration
  def randomFactor: Double
}

object WorkerConfig {
  def apply(config: Config): WorkerConfig = new WorkerConfigImpl(config.getConfig("lagom.projection.worker"))

  private final class WorkerConfigImpl(config: Config) extends WorkerConfig {
    val minBackoff: FiniteDuration = config.getDuration("backoff.supervisor.minBackoff", TimeUnit.MILLISECONDS).millis
    val maxBackoff: FiniteDuration = config.getDuration("backoff.supervisor.maxBackoff", TimeUnit.MILLISECONDS).millis
    val randomFactor: Double       = config.getDouble("backoff.supervisor.randomFactor")
  }
}
