/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import scala.collection.immutable

abstract class CassandraConfig private[lagom] () {

  /** Returns the Cassandra contact-points. */
  def uris: immutable.Seq[CassandraContactPoint]

}
