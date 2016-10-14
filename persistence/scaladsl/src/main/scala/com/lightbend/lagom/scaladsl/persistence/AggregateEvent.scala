/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

/**
 * The base type of [[PersistentEntity]] events may implement this
 * interface to make the events available for read-side processing.
 */
trait AggregateEvent[E <: AggregateEvent[E]] {
  def aggregateTag: AggregateEventTagger[E]
}
