/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.spi;


public interface CircuitBreakerMetrics {

  /**
   * Invoked when the circuit breaker transitions to the open state.
   */
  void onOpen();

  /**
   * Invoked when the circuit breaker transitions to the close state.
   */
  void onClose();

  /**
   * Invoked when the circuit breaker transitions to the half-open
   * state after reset timeout.
   */
  void onHalfOpen();

  /**
   * Invoked for each successful call.
   *
   * @param elapsedNanos the elapsed duration of the call in nanoseconds
   */
  void onCallSuccess(long elapsedNanos);

  /**
   * Invoked for each call when the future is completed with exception,
   * except for `TimeoutException` and `CircuitBreakerOpenException` that
   * are handled by separate methods.
   *
   * @param elapsedNanos the elapsed duration of the call in nanoseconds
   */
  void onCallFailure(long elapsedNanos);

  /**
   * Invoked for each call when the future is completed with
   * `java.util.concurrent.TimeoutException`
   *
   * @param elapsedNanos the elapsed duration of the call in nanoseconds
   */
  void onCallTimeoutFailure(long elapsedNanos);

  /**
   * Invoked for each call when the future is completed with
   * `akka.pattern.CircuitBreakerOpenException`
   */
  void onCallBreakerOpenFailure();

  /**
   * Called when the circuit breaker is removed, e.g. expired due to inactivity.
   * It is also called if the circuit breaker is re-configured, before calling
   * {@link CircuitBreakerMetricsProvider#start}.
   */
  void stop();

}
