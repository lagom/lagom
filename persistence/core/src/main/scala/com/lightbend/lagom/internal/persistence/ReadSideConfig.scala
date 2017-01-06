/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import play.api.Configuration

import scala.concurrent.duration._

case class ReadSideConfig(
  minBackoff:           FiniteDuration = 3.seconds,
  maxBackoff:           FiniteDuration = 30.seconds,
  randomBackoffFactor:  Double         = 0.2,
  globalPrepareTimeout: FiniteDuration = 20.seconds,
  role:                 Option[String] = None
)

object ReadSideConfig {
  def apply(configuration: Configuration): ReadSideConfig =
    apply(configuration.underlying.getConfig("lagom.persistence.read-side"))

  def apply(conf: Config): ReadSideConfig = {
    ReadSideConfig(
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
