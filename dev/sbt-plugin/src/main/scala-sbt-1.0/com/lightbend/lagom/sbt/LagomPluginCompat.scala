/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.sbt

import scala.concurrent.duration.Duration

trait LagomPluginCompat {
  def getPollInterval(pollInterval: Duration) = pollInterval.toMillis.toInt
}
