/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import scala.concurrent.Future

/**
 * Internal API:
 *
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
