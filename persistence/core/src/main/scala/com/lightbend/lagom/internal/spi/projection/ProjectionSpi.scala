/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.spi.projection

import akka.actor.ActorSystem
import akka.annotation.InternalStableApi
import akka.persistence.query.EventEnvelope
import akka.persistence.query.Offset

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object ProjectionSpi {

  @InternalStableApi
  private[lagom] def startProcessing(system: ActorSystem, tagName: String, envelope: EventEnvelope): EventEnvelope =
    envelope

  @InternalStableApi
  private[lagom] def afterUserFlow(projectionName: String, tagName: String, offset: Offset): Offset = offset

  @InternalStableApi
  private[lagom] def completedProcessing(
      projectionName: String,
      tagName: String,
      offset: Offset
  ): Offset = offset

  @InternalStableApi
  private[lagom] def failed(
      actorSystem: ActorSystem,
      projectionName: String,
      partitionName: String,
      exception: Throwable
  ): Unit = ()

}
