/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraProvider, CassandraOffsetStore }
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.ExecutionContext

/**
 * Internal API
 */
private[lagom] final class ScaladslCassandraOffsetStore(system: ActorSystem, session: CassandraSession,
                                                        cassandraConfigProvider: CassandraProvider,
                                                        config:                  ReadSideConfig)(implicit ec: ExecutionContext)
  extends CassandraOffsetStore(system, session.delegate, cassandraConfigProvider, config)
