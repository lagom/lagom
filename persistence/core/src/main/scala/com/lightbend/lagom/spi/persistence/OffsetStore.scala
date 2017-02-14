/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.spi.persistence

import akka.Done
import akka.persistence.query.{ NoOffset, Offset }

import scala.concurrent.Future
import scala.collection.concurrent

/**
 * Offset store implementation.
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

/**
 * Not for production use.
 */
class InMemoryOffsetStore extends OffsetStore {
  private final val store: concurrent.Map[String, Offset] = concurrent.TrieMap.empty

  override def prepare(eventProcessorId: String, tag: String): Future[OffsetDao] = {
    val key = s"$eventProcessorId-$tag"
    Future.successful(new OffsetDao {
      override def saveOffset(offset: Offset): Future[Done] = {
        store.put(key, offset)
        Future.successful(Done)
      }
      override val loadedOffset: Offset = store.getOrElse(key, NoOffset)
    })
  }
}
