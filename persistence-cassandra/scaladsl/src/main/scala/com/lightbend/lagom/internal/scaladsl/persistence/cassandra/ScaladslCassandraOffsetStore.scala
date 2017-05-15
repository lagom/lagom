/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraReadSideSettings, CassandraOffsetStore }
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.ExecutionContext

/**
 * Internal API
 */
private[lagom] final class ScaladslCassandraOffsetStore(system: ActorSystem, session: CassandraSession,
                                                        cassandraReadSideSettings: CassandraReadSideSettings,
                                                        config:                    ReadSideConfig)(implicit ec: ExecutionContext)
  extends CassandraOffsetStore(system, session.delegate, cassandraReadSideSettings, config)
