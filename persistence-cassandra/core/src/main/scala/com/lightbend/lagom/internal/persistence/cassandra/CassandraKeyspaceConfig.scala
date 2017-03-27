/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.event.LoggingAdapter
import com.typesafe.config.Config

private[lagom] object CassandraKeyspaceConfig {

  def validateKeyspace(namespace: String)(implicit config: Config, log: LoggingAdapter): Unit =
    validateKeyspace(namespace, s"$namespace.defaults")

  def validateKeyspace(namespace: String, defaultNamespace: String)(implicit config: Config, log: LoggingAdapter): Unit = {
    val path = s"$namespace.keyspace"
    if (!config.hasPath(path)) {
      log.error("Configuration for [{}] must be set in application.conf", path)
    } else if (log.isWarningEnabled) {
      val keyspace = config.getString(path)
      val defaultKeyspace = config.getString(s"$defaultNamespace.keyspace")
      if (keyspace == defaultKeyspace) {
        log.warning(
          "Configuration for [{}] is using deprecated default value [{}]. " +
            "Please set an explicit keyspace value in application.conf",
          path,
          defaultKeyspace
        )
      }
    }
  }

}
