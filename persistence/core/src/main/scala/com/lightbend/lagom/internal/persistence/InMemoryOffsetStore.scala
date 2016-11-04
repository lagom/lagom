/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import javax.inject.Singleton

import akka.Done
import akka.persistence.query.{ NoOffset, Offset }

import scala.collection.concurrent
import scala.concurrent.Future

/**
 * Not for production use.
 */
@Singleton
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
