/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import javax.inject.Inject
import akka.actor.ActorSystem

/**
 * Internal API
 */
private[lagom] class CassandraReadSideSettings @Inject() (system: ActorSystem) {
  private val cassandraConfig = system.settings.config.getConfig("lagom.persistence.read-side.cassandra")

  val autoCreateTables: Boolean = cassandraConfig.getBoolean("tables-autocreate")
}
