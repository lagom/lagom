/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import org.pcollections.PSet

trait CassandraConfig {

  /** Returns the Cassandra contact-points. */
  def uris: PSet[CassandraContactPoint]

}
