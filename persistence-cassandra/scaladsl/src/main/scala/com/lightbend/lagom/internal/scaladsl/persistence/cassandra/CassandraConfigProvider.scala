/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import java.net.URI
import javax.inject.{ Inject, Provider, Singleton }

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorSessionProvider
import com.lightbend.lagom.scaladsl.persistence.cassandra.{ CassandraConfig, CassandraContactPoint }

import scala.collection.immutable

/**
 * Internal API
 */
// Injecting ActorSystem and not Configuration because Configuration isn't always bound when running tests
@Singleton
final class CassandraConfigProvider @Inject() (system: ActorSystem) extends Provider[CassandraConfig] {
  private val config = system.settings.config

  override lazy val get: CassandraConfig = CassandraConfigProvider.CassandraConfigImpl(cassandraUrisFromConfig)

  private def cassandraUrisFromConfig: immutable.Seq[CassandraContactPoint] = {
    List("cassandra-journal", "cassandra-snapshot-store", "lagom.persistence.read-side.cassandra").flatMap { path =>
      val c = config.getConfig(path)
      if (c.getString("session-provider") == classOf[ServiceLocatorSessionProvider].getName) {
        val name = c.getString("cluster-id")
        val port = c.getInt("port")
        val uri = new URI(s"tcp://127.0.0.1:$port/$name")
        Some(CassandraContactPoint(name, uri))
      } else None
    }
  }
}

private object CassandraConfigProvider {
  case class CassandraConfigImpl(uris: immutable.Seq[CassandraContactPoint]) extends CassandraConfig
}
