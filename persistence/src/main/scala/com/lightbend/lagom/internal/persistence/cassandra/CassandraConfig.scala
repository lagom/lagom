/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.net.URI

import scala.collection.JavaConverters.setAsJavaSetConverter
import scala.language.implicitConversions

import org.pcollections.HashTreePSet
import org.pcollections.PSet

import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraConfig
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraContactPoint

import akka.actor.ActorSystem
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Injecting ActorSystem and not Configuration because Configuration isn't always bound when running tests
@Singleton
class CassandraConfigProvider @Inject() (system: ActorSystem) extends Provider[CassandraConfig] {
  private val config = system.settings.config

  override lazy val get: CassandraConfig = CassandraConfigProvider.CassandraConfigImpl(cassandraUrisFromConfig)

  private def cassandraUrisFromConfig: PSet[CassandraContactPoint] = {
    val contactPoints = List("cassandra-journal", "cassandra-snapshot-store", "lagom.persistence.read-side.cassandra").flatMap { path =>
      val c = config.getConfig(path)
      if (c.getString("session-provider") == classOf[ServiceLocatorSessionProvider].getName) {
        val name = c.getString("cluster-id")
        val port = c.getInt("port")
        val uri = new URI(s"tcp://127.0.0.1:$port/$name")
        Some(CassandraContactPoint.of(name, uri))
      } else None
    }.toSet
    HashTreePSet.from(contactPoints.asJava)
  }
}

private object CassandraConfigProvider {
  case class CassandraConfigImpl(uris: PSet[CassandraContactPoint]) extends CassandraConfig
}
