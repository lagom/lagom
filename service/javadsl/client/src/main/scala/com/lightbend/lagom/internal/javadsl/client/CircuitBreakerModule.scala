/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client

import javax.inject.{ Inject, Provider, Singleton }

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakerMetricsProviderProvider, CircuitBreakers }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel
import play.api.inject.{ Binding, Module }
import play.api.{ Configuration, Environment }

class CircuitBreakerModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[CircuitBreakersPanel].to[CircuitBreakersPanelImpl],
      // for backward compatibility we still need to provide it for wiring
      bind[CircuitBreakers].toProvider[CircuitBreakersProvider],
      bind[CircuitBreakerMetricsProvider].toProvider[CircuitBreakerMetricsProviderProvider]
    )
  }
}

@Singleton
class CircuitBreakersProvider @Inject() (
  system:               ActorSystem,
  circuitBreakerConfig: CircuitBreakerConfig,
  metricsProvider:      CircuitBreakerMetricsProvider
) extends Provider[CircuitBreakers] {
  lazy val get = {

    // only in case some 3rd part lib are still wiring the old one.
    system.log.warning(
      """
        | +----------------------------------------------------------------------------------------+
        | | com.lightbend.lagom.internal.client.CircuitBreakers is deprecated                      |
        | | use com.lightbend.lagom.javadsl.client.CircuitBreakersPanel instead.                   |
        | |                                                                                        |
        | | If you don't understand why you are getting this warning, it's probably because        |
        | | you are using a 3rd party library that is still wiring this deprecated CircuitBreakers.|
        | | Make sure to upgrade the libraries providing your service locators.                    |
        | +----------------------------------------------------------------------------------------+
      """.stripMargin

    new CircuitBreakers(system, circuitBreakerConfig, metricsProvider)
  }
}