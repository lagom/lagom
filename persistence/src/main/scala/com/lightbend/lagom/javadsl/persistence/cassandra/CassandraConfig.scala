/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

trait CassandraConfig {
  /**
   * Returns the Cassandra contact-points as a collection of pairs.
   * Each contact-point (i.e., each pair) is composed of a cluster ID and it's full URL.
   */
  def uris: Set[(String, String)]
}
