/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import java.net.URI

import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorSessionProvider
import com.lightbend.lagom.scaladsl.persistence.cassandra.{ CassandraConfig, CassandraContactPoint }
import com.typesafe.config.Config

import scala.collection.immutable

/**
 * Internal API
 */
@deprecated(message = "This class became obsolete and will be removed on next release", since = "1.4.0")
private[lagom] object CassandraConfigImpl {

  private def apply(config: Config): CassandraConfigImpl = {
    val contactPoints = List("cassandra-journal", "cassandra-snapshot-store", "lagom.persistence.read-side.cassandra").flatMap { path =>
      val c = config.getConfig(path)
      if (c.getString("session-provider") == classOf[ServiceLocatorSessionProvider].getName) {
        val name = c.getString("cluster-id")
        val port = c.getInt("port")
        val uri = new URI(s"tcp://127.0.0.1:$port/$name")
        Some(CassandraContactPoint(name, uri))
      } else None
    }
    new CassandraConfigImpl(contactPoints)
  }
}
@deprecated(message = "This class became obsolete and will be removed on next release", since = "1.4.0")
private[lagom] final case class CassandraConfigImpl(uris: immutable.Seq[CassandraContactPoint]) extends CassandraConfig
