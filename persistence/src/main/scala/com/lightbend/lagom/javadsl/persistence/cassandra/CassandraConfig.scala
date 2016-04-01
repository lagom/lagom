/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.net.URI
import org.pcollections.PSet

trait CassandraConfig {

  /** Returns the Cassandra contact-points. */
  def uris: PSet[CassandraContactPoint]

}
