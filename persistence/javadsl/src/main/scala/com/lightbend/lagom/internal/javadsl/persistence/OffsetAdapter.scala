/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import akka.persistence.query.{ NoOffset, Offset, Sequence, TimeBasedUUID }
import com.lightbend.lagom.javadsl.persistence.{ Offset => LagomJavaDslOffset }

/**
 * Internal API
 *
 * Converts between the Akka Persistence Query Offset type and the internal Lagom Java Offset type
 */
object OffsetAdapter {

  def offsetToDslOffset(offset: Offset): LagomJavaDslOffset = offset match {
    case TimeBasedUUID(uuid) => LagomJavaDslOffset.timeBasedUUID(uuid)
    case Sequence(value)     => LagomJavaDslOffset.sequence(value)
    case NoOffset            => LagomJavaDslOffset.NONE
    case _                   => throw new IllegalArgumentException("Unsuppoerted offset type " + offset.getClass.getName)
  }

  def dslOffsetToOffset(dslOffset: LagomJavaDslOffset): Offset = dslOffset match {
    case uuid: LagomJavaDslOffset.TimeBasedUUID => TimeBasedUUID(uuid.value())
    case seq: LagomJavaDslOffset.Sequence       => Sequence(seq.value())
    case LagomJavaDslOffset.NONE                => NoOffset
    case _                                      => throw new IllegalArgumentException("Unsuppoerted offset type " + dslOffset.getClass.getName)
  }

}
