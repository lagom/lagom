/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetStore
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.ExecutionContext

/**
 * Internal API
 */
private[lagom] final class ScaladslCassandraOffsetStore(system: ActorSystem, session: CassandraSession,
                                                        config: ReadSideConfig)(implicit ec: ExecutionContext)
  extends CassandraOffsetStore(system, session.delegate, config)