/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

/**
 * Configuration keys and settings
 */
object LagomConfig {
  val ServiceLocatorUrl = "lagom.service-locator.url"

  private def cassandraConfig(key: String, value: String) = Map(
    s"cassandra-journal.defaults.$key" -> value,
    s"cassandra-snapshot-store.defaults.$key" -> value,
    s"lagom.defaults.persistence.read-side.cassandra.$key" -> value
  )

  def cassandraPort(port: Int): Map[String, String] = cassandraConfig("port", port.toString)

  def actorSystemConfig(name: String) = Map(
    "lagom.akka.dev-mode.actor-system.name" -> s"$name-internal-dev-mode",
    "play.akka.actor-system" -> s"$name-application",
    "lagom.defaults.cluster.join-self" -> "on"
  )

}
