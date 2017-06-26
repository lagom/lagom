/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakersPanelInternal }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.scaladsl.client.CircuitBreakersPanel

import scala.concurrent.Future

class CircuitBreakersPanelImpl(circuitBreakersInternal: CircuitBreakersPanelInternal)
  extends CircuitBreakersPanel {

  def this(system: ActorSystem, config: CircuitBreakerConfig, metricsProvider: CircuitBreakerMetricsProvider) =
    this(new CircuitBreakersPanelInternal(system, config, metricsProvider))

  override def withCircuitBreaker[T](id: String)(body: => Future[T]): Future[T] =
    circuitBreakersInternal.withCircuitBreaker(id)(body)
}

object CircuitBreakersConverter {
  /** */
  def toScaladslCircuitBreakersPanel(circuitBreakers: com.lightbend.lagom.internal.client.CircuitBreakers): CircuitBreakersPanel = {
    new CircuitBreakersPanelImpl(circuitBreakers)
  }
}
