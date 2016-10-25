/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import java.util.UUID

/**
 * An offset.
 *
 * Offsets are used for ordering of events, typically in event journals, so that consumers can keep track of what events
 * they have and haven't consumed.
 *
 * Akka persistence, which underlies Lagom's persistence APIs, uses different offset types for different persistence
 * datastores. This class provides an abstraction over them. The two types currently supported are a <code>long</code>
 * sequence number and a time based <code>UUID</code>.
 *
 * FIXME
 */
trait Offset

final case class Sequence(value: Long) extends Offset with Ordered[Sequence] {
  override def compare(that: Sequence): Int = value.compare(that.value)
}

final case class TimeBasedUUID(value: UUID) extends Offset with Ordered[TimeBasedUUID] {
  if (value == null || value.version != 1) {
    throw new IllegalArgumentException("UUID " + value + " is not a time-based UUID")
  }

  override def compare(other: TimeBasedUUID): Int = value.compareTo(other.value)
}

final case object NoOffset extends Offset
