/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class ReadSideConfig(
  minBackoff:           FiniteDuration = 3.seconds,
  maxBackoff:           FiniteDuration = 30.seconds,
  randomBackoffFactor:  Double         = 0.2,
  globalPrepareTimeout: FiniteDuration = 20.seconds,
  role:                 Option[String] = None
)