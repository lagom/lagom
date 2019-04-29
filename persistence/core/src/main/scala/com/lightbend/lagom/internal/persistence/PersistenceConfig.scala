/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence

import com.typesafe.config.Config

import scala.concurrent.duration._

case class PersistenceConfig(
  maxNumberOfShards:         Int,
  snapshotAfter:             Option[Int],
  passivateAfterIdleTimeout: Duration,
  runEntitiesOnRole:         Option[String],
  askTimeout:                FiniteDuration,
  delayRegistration:         Boolean
)

object PersistenceConfig {
  def apply(config: Config): PersistenceConfig = {
    new PersistenceConfig(
      maxNumberOfShards = config.getInt("max-number-of-shards"),
      snapshotAfter = config.getString("snapshot-after") match {
        case "off" => None
        case _     => Some(config.getInt("snapshot-after"))
      },
      passivateAfterIdleTimeout = {
        val duration = config.getDuration("passivate-after-idle-timeout").toMillis.millis
        if (duration == Duration.Zero) Duration.Undefined else duration
      },
      runEntitiesOnRole = Some(config.getString("run-entities-on-role")).filter(_.nonEmpty),
      askTimeout = config.getDuration("ask-timeout").toMillis.millis,
      delayRegistration = config.getBoolean("delay-registration")
    )
  }
}
