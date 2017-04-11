/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.event.LoggingAdapter
import com.typesafe.config.Config

private[lagom] object CassandraKeyspaceConfig {

  def validateKeyspace(namespace: String, config: Config, log: LoggingAdapter): Unit = {
    if (log.isErrorEnabled) {
      val keyspacePath = s"$namespace.keyspace"
      if (!config.hasPath(keyspacePath)) {
        log.error("Configuration for [{}] must be set in application.conf ", keyspacePath)
      }
    }
  }

}
