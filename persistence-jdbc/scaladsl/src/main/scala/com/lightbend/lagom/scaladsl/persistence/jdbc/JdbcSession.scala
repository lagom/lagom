/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import java.sql.Connection

import scala.concurrent.Future

object JdbcSession {
  /**
   * Closes this resource after the block has finished.
   */
  def tryWith[Resource <: AutoCloseable, Out](resource: Resource)(block: Resource => Out): Out = {
    try {
      block(resource)
    } finally {
      resource.close()
    }
  }
}

trait JdbcSession {
  /**
   * Execute the given function with a connection.
   *
   * This will execute the callback in a thread pool that is specifically designed for use with JDBC calls.
   *
   * @param block The block to execute.
   * @return A future of the result.
   */
  def withConnection[T](block: Connection => T): Future[T]

  /**
   * Execute the given function in a transaction.
   *
   * This will execute the callback in a thread pool that is specifically designed for use with JDBC calls.
   *
   * @param block The block to execute.
   * @return A future of the result.
   */
  def withTransaction[T](block: Connection => T): Future[T]
}
