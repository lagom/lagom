/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import akka.Done
import akka.persistence.query.Offset

import scala.concurrent.Future

/**
 * Internal API:
 *
 * A prepared DAO for storing offsets.
 */
trait OffsetDao {

  /**
   * The last offset processed.
   */
  val loadedOffset: Offset

  /**
   * Save the given offset.
   *
   * @param offset The offset to save.
   * @return A future that is redeemed when the offset has been saved.
   */
  def saveOffset(offset: Offset): Future[Done]
}
