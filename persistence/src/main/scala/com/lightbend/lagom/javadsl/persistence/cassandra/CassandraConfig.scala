/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.net.URI

trait CassandraConfig {

  /** Returns the Cassandra contact-points. */
  def uris: Set[CassandraContactPoint]

}
