/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import scala.annotation.tailrec
import akka.persistence.journal.WriteEventAdapter
import akka.persistence.journal.WriteEventAdapter
import akka.persistence.journal.Tagged

/**
 * The base type of [[PersistentEntity]] events may implement this
 * interface to make the events available for read-side processing.
 */
trait AggregateEvent[E <: AggregateEvent[E]] {
  def aggregateTag: AggregateEventTag[E]
}
