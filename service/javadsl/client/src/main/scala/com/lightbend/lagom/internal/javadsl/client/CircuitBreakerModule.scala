/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.client

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.client.CircuitBreakerConfig
import com.lightbend.lagom.internal.client.CircuitBreakerMetricsProviderImpl
import com.lightbend.lagom.internal.client.CircuitBreakerMetricsProviderProvider
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel
import play.api.inject.Binding
import play.api.inject.Module
import play.api.Configuration
import play.api.Environment

class CircuitBreakerModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[CircuitBreakersPanel].to[CircuitBreakersPanelImpl],
      bind[CircuitBreakerMetricsProvider].toProvider[CircuitBreakerMetricsProviderProvider],
      bind[CircuitBreakerConfig].toSelf,
      bind[CircuitBreakerMetricsProviderImpl].toSelf
    )
  }
}
