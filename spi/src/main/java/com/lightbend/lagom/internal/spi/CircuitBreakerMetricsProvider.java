/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.spi;

/**
 * Service Provider Interface (SPI) for collecting metrics from circuit
 * breakers.
 */
public interface CircuitBreakerMetricsProvider {
  /**
   * Start metrics collection for the circuit breaker with `breakerId`
   * identifier. Create (new or existing) instance of a
   * {@link CircuitBreakerMetrics} that will be used for this circuit breaker
   * instance.
   *
   * The methods of the `CircuitBreakerMetrics` will be invoked when the circuit
   * breaker is used or changes state. {@link CircuitBreakerMetrics#stop} is
   * called when the circuit breaker is removed, e.g. expired due to inactivity.
   *
   * `stop` and `start` are also also invoked if the circuit breaker is
   * re-configured.
   */
  CircuitBreakerMetrics start(String breakerId);
}
