/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence

import akka.annotation.InternalStableApi
import akka.persistence.query.NoOffset
import akka.persistence.query.Offset
import akka.persistence.query.Sequence
import akka.persistence.query.TimeBasedUUID
import com.lightbend.lagom.javadsl.persistence.{ Offset => LagomJavaDslOffset }

/**
 * Internal API
 *
 * Converts between the Akka Persistence Query Offset type and the internal Lagom Java Offset type
 */
object OffsetAdapter {

  @InternalStableApi
  def offsetToDslOffset(offset: Offset): LagomJavaDslOffset = offset match {
    case TimeBasedUUID(uuid) => LagomJavaDslOffset.timeBasedUUID(uuid)
    case Sequence(value)     => LagomJavaDslOffset.sequence(value)
    case NoOffset            => LagomJavaDslOffset.NONE
    case _                   => throw new IllegalArgumentException("Unsupported offset type " + offset.getClass.getName)
  }

  @InternalStableApi
  def dslOffsetToOffset(dslOffset: LagomJavaDslOffset): Offset = dslOffset match {
    case uuid: LagomJavaDslOffset.TimeBasedUUID => TimeBasedUUID(uuid.value())
    case seq: LagomJavaDslOffset.Sequence       => Sequence(seq.value())
    case LagomJavaDslOffset.NONE                => NoOffset
    case _                                      => throw new IllegalArgumentException("Unsupported offset type " + dslOffset.getClass.getName)
  }

}
