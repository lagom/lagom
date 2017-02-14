/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

/**
 * The base type of [[PersistentEntity]] events may implement this
 * interface to make the events available for read-side processing.
 */
trait AggregateEvent[E <: AggregateEvent[E]] {
  def aggregateTag: AggregateEventTagger[E]
}
