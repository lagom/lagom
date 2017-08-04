/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import org.pcollections.PSet

@deprecated(message = "This class became obsolete and will be removed on next release", since = "1.4.0")
trait CassandraConfig {

  /** Returns the Cassandra contact-points. */
  def uris: PSet[CassandraContactPoint]

}
