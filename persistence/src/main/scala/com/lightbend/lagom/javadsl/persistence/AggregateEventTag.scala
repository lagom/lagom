/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import scala.annotation.tailrec
import akka.persistence.journal.WriteEventAdapter
import akka.persistence.journal.WriteEventAdapter
import akka.persistence.journal.Tagged

object AggregateEventTag {
  /**
   * Convenience factory method of [[AggregateEventTag]] that uses the
   * class name of the event type as `tag`. Note that it is needed to
   * retain the original tag when the class name is changed because
   * the tag is part of the store event data.
   */
  def of[Event <: AggregateEvent[Event]](eventType: Class[Event]): AggregateEventTag[Event] =
    new AggregateEventTag(eventType, eventType.getName)

  /**
   * Factory method of [[AggregateEventTag]].
   */
  def of[Event <: AggregateEvent[Event]](eventType: Class[Event], tag: String): AggregateEventTag[Event] =
    new AggregateEventTag(eventType, tag)
}

/**
 * The base type of [[PersistentEntity]] events may implement this
 * interface to make the events available for read-side processing.
 *
 * The `tag` should be unique among the event types of the service.
 *
 * The class name can be used as `tag`, but note that it is needed
 * to retain the original tag when the class name is changed because
 * the tag is part of the store event data.
 */
final class AggregateEventTag[Event <: AggregateEvent[Event]](
  val eventType: Class[Event],
  val tag:       String
)
