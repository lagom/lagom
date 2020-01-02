/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.spi;

/** Service Provider Interface (SPI) for collecting metrics from circuit breakers. */
public interface CircuitBreakerMetricsProvider {
  /**
   * Start metrics collection for the circuit breaker with `breakerId` identifier. Create (new or
   * existing) instance of a {@link CircuitBreakerMetrics} that will be used for this circuit
   * breaker instance.
   *
   * <p>The methods of the `CircuitBreakerMetrics` will be invoked when the circuit breaker is used
   * or changes state. {@link CircuitBreakerMetrics#stop} is called when the circuit breaker is
   * removed, e.g. expired due to inactivity.
   *
   * <p>`stop` and `start` are also also invoked if the circuit breaker is re-configured.
   *
   * @param breakerId the identifider for the circuit breaker.
   * @return the metrics for the circuit breaker with the given identifier
   */
  CircuitBreakerMetrics start(String breakerId);
}
