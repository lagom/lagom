/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.lagom.internal

import scala.concurrent.ExecutionContext
import akka.annotation.InternalApi

import scala.concurrent.Future
import akka.Done

/**
 *
 */
@InternalApi object SerializedExecutionAccessor {

  def serializedExecution(
      recur: () => Future[Done],
      exec: () => Future[Done]
  )(implicit ec: ExecutionContext): Future[Done] = {

    akka.persistence.cassandra.session.scaladsl.CassandraSession.serializedExecution(
      recur,
      exec
    )
  }
}
