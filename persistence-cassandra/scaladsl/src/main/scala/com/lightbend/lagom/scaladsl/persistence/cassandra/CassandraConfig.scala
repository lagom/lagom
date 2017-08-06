/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.net.URI

import scala.collection.immutable

@deprecated(message = "This class became obsolete and will be removed on next release", since = "1.4.0")
abstract class CassandraConfig private[lagom] () {

  /** Returns the Cassandra contact-points. */
  def uris: immutable.Seq[CassandraContactPoint]

}

@deprecated(message = "This class became obsolete and will be removed on next release", since = "1.4.0")
final case class CassandraContactPoint(name: String, uri: URI) {
  require(name != null, "name must not be null")
  require(uri != null, "uri must not be null")
}
