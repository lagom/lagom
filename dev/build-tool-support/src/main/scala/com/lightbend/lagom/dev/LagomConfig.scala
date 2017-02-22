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
  def cassandraKeySpace(keyspace: String) = cassandraConfig("keyspace", keyspace)
  def cassandraPort(port: Int) = cassandraConfig("port", port.toString)

  def actorSystemConfig(name: String) = Map(
    "lagom.akka.dev-mode.actor-system.name" -> s"$name-internal-dev-mode",
    "play.akka.actor-system" -> s"$name-application",
    "lagom.defaults.cluster.join-self" -> "on"
  )

  private val cassandraKeyspaceNameRegex = """^("[a-zA-Z]{1}[\w]{0,47}"|[a-zA-Z]{1}[\w]{0,47})$""".r

  def normalizeCassandraKeyspaceName(projectName: String): String = {
    def isValidKeyspaceName(name: String): Boolean = cassandraKeyspaceNameRegex.pattern.matcher(name).matches()
    if (isValidKeyspaceName(projectName)) projectName
    else {
      // I'm confident the normalized name will work in most situations. If it doesn't, then
      // the application will fail at runtime and users will have to provide a valid keyspace
      // name in the application.conf
      val normalizedName = projectName.replaceAll("""[^\w]""", "_")
      normalizedName
    }
  }
}
