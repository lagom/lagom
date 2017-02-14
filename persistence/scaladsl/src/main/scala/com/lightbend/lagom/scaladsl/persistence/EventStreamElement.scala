/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.persistence.query.Offset

object EventStreamElement {
  def unapply[Event](elem: EventStreamElement[Event]): Option[(String, Event, Offset)] =
    Some((elem.entityId, elem.event, elem.offset))
}

/**
 * Envelope for events in the eventstream, provides additional data to the actual event
 */
final class EventStreamElement[+Event](
  val entityId: String,
  val event:    Event,
  val offset:   Offset
) {

  override def equals(other: Any): Boolean = other match {
    case that: EventStreamElement[_] =>
      entityId == that.entityId &&
        event == that.event &&
        offset == that.offset
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq[Any](entityId, event, offset)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString() = s"EventStreamElement($entityId, $event, $offset)"

}
