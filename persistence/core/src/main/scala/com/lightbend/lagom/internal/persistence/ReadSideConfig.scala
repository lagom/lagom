/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration._

case class ReadSideConfig(
    offsetTimeout: FiniteDuration = 5.seconds,
    minBackoff: FiniteDuration = 3.seconds,
    maxBackoff: FiniteDuration = 30.seconds,
    randomBackoffFactor: Double = 0.2,
    globalPrepareTimeout: FiniteDuration = 20.seconds,
    role: Option[String] = None
)

object ReadSideConfig {

  def apply(conf: Config): ReadSideConfig = {
    ReadSideConfig(
      conf.getDuration("offset-timeout", TimeUnit.MILLISECONDS).millis,
      conf.getDuration("failure-exponential-backoff.min", TimeUnit.MILLISECONDS).millis,
      conf.getDuration("failure-exponential-backoff.max", TimeUnit.MILLISECONDS).millis,
      conf.getDouble("failure-exponential-backoff.random-factor"),
      conf.getDuration("global-prepare-timeout", TimeUnit.MILLISECONDS).millis,
      conf.getString("run-on-role") match {
        case "" => None
        case r  => Some(r)
      }
    )
  }

}
