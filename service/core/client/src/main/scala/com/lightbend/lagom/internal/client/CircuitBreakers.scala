/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.typesafe.config.Config
import play.api.Configuration

import scala.concurrent.Future

@deprecated(message = "Use com.lightbend.lagom.javadsl.client.CircuitBreakersPanel or com.lightbend.lagom.scaladsl.client.CircuitBreakersPanel instead", since = "1.4.0")
@Singleton
class CircuitBreakers @Inject() (
  system:               ActorSystem,
  circuitBreakerConfig: CircuitBreakerConfig,
  metricsProvider:      CircuitBreakerMetricsProvider
) extends CircuitBreakersPanelInternal(system, circuitBreakerConfig, metricsProvider)
