/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.event.LoggingAdapter
import com.typesafe.config.Config

private[lagom] object CassandraKeyspaceConfig {

  def validateKeyspace(namespace: String, defaultNamespace: String, config: Config, log: LoggingAdapter): Unit = {
    if (log.isWarningEnabled) {
      val keyspace = config.getString(s"$namespace.keyspace")
      val defaultPath = s"$defaultNamespace.keyspace"
      if (config.hasPath(defaultPath) && keyspace == config.getString(defaultPath)) {
        log.warning(
          "Configuration for [{}] is using deprecated default value [{}]. " +
            "Please set an explicit keyspace value in application.conf",
          s"$namespace.keyspace",
          keyspace
        )
      }
    }
  }

}
