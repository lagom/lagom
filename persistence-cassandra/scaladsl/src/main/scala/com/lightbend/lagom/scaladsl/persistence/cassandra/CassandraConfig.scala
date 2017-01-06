/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.net.URI

import scala.collection.immutable

abstract class CassandraConfig private[lagom] () {

  /** Returns the Cassandra contact-points. */
  def uris: immutable.Seq[CassandraContactPoint]

}

final case class CassandraContactPoint(name: String, uri: URI) {
  require(name != null, "name must not be null")
  require(uri != null, "uri must not be null")
}
