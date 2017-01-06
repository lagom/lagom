/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server.status;

import akka.stream.javadsl.Source;

import java.util.List;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Service;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface MetricsService extends Service {

  /**
   * Snapshot of current circuit breaker status
   */
  ServiceCall<NotUsed, List<CircuitBreakerStatus>> currentCircuitBreakers();
  
  /**
   * Stream of circuit breaker status
   */
  ServiceCall<NotUsed, Source<List<CircuitBreakerStatus>, ?>> circuitBreakers();

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("/metrics").withCalls(
        pathCall("/_status/circuit-breaker/current", this::currentCircuitBreakers),
        pathCall("/_status/circuit-breaker/stream", this::circuitBreakers)
    ).withLocatableService(false);
    // @formatter:on
  }
}
