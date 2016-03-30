/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import scala.language.implicitConversions

import akka.actor.ActorSystem
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

trait CassandraConfig {
  def uris: Set[(String, String)]
}

object CassandraConfig {
  // Injecting ActorSystem and not Configuration because Configuration isn't always bound when running tests
  @Singleton
  class CassandraConfigProvider @Inject() (system: ActorSystem) extends Provider[CassandraConfig] {
    private val config = system.settings.config

    override lazy val get: CassandraConfig = CassandraConfigImpl(cassandraUrisFromConfig)

    private def cassandraUrisFromConfig: Set[(String, String)] = {
      List("cassandra-journal", "cassandra-snapshot-store", "lagom.persistence.read-side.cassandra").flatMap { path =>
        val c = config.getConfig(path)
        if (c.getString("session-provider") == classOf[ServiceLocatorSessionProvider].getName) {
          val name = c.getString("cluster-id")
          val port = c.getInt("port")
          val uri = s"tcp://127.0.0.1:$port/$name"
          Some(name -> uri)
        } else None
      }.toSet
    }

    import scala.language.implicitConversions
    private implicit def asFiniteDuration(d: java.time.Duration) =
      scala.concurrent.duration.Duration.fromNanos(d.toNanos)
  }

  private[this] case class CassandraConfigImpl(uris: Set[(String, String)]) extends CassandraConfig
}
