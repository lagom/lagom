/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.spi.projections

import akka.persistence.query.Offset

import scala.concurrent.Future
import akka.annotation.InternalStableApi
import akka.persistence.query.EventEnvelope

import scala.concurrent.ExecutionContext

object ProjectionsSpi {
  @InternalStableApi
  private[lagom] def startProcessing(tagName: String, envelope: EventEnvelope): EventEnvelope = envelope
  @InternalStableApi
  private[lagom] def afterUserFlow[Message](messageAndOffset: (Message, Offset)): (Message, Offset) = messageAndOffset
  @InternalStableApi
  private[lagom] def completedProcessing(offset: Future[Offset], exCtx: ExecutionContext): Future[Offset] = offset

}
