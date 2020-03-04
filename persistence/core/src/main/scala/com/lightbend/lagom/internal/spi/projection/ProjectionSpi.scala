/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.spi.projection

import akka.actor.ActorSystem
import akka.persistence.query.Offset

import scala.concurrent.Future
import akka.annotation.InternalStableApi
import akka.persistence.query.EventEnvelope

import scala.concurrent.ExecutionContext

object ProjectionSpi {
  @InternalStableApi
  private[lagom] def startProcessing(system: ActorSystem, tagName: String, envelope: EventEnvelope): EventEnvelope =
    envelope

  @InternalStableApi
  private[lagom] def afterUserFlow(projectionName: String, offset: Offset): Offset = offset

  @InternalStableApi
  private[lagom] def completedProcessing(offset: Future[Offset], exCtx: ExecutionContext): Future[Offset] = offset

}
