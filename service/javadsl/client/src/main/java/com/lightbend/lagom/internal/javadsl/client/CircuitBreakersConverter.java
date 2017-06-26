/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client;

import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel;

public class CircuitBreakersConverter {

  /**
   * Temporary converter utility to convert {@link com.lightbend.lagom.internal.client.CircuitBreakers} to {@link CircuitBreakersPanel}
   */
  public static CircuitBreakersPanel toJavadslCircuitBreakerInvoker(com.lightbend.lagom.internal.client.CircuitBreakers circuitBreakers) {
    return new CircuitBreakersPanelImpl(circuitBreakers);
  }
}
