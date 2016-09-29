/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import akka.Done
import com.lightbend.lagom.javadsl.persistence.Offset

import scala.concurrent.Future

/**
 * Offset store implementation
 */
trait OffsetStore {

  /**
   * Prepare this offset store to process the given ID and tag.
   *
   * @param eventProcessorId The ID of the event processor.
   * @param tag The tag to prepare for.
   * @return The DAO, with the loaded offset.
   */
  def prepare(eventProcessorId: String, tag: String): Future[OffsetDao]
}

/**
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
