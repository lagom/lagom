/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import org.pcollections.PSet

trait CassandraConfig {

  /** Returns the Cassandra contact-points. */
  def uris: PSet[CassandraContactPoint]

}
