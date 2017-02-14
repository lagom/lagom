/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server.status;

import com.codahale.metrics.Snapshot;
import com.lightbend.lagom.internal.client.CircuitBreakerMetricsImpl;
import com.lightbend.lagom.internal.client.CircuitBreakerMetricsProviderImpl;
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.server.status.CircuitBreakerStatus;
import com.lightbend.lagom.javadsl.server.status.Latency;
import com.lightbend.lagom.javadsl.server.status.MetricsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import scala.concurrent.duration.FiniteDuration;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Source;

public class MetricsServiceImpl implements MetricsService {
  
  private final Optional<CircuitBreakerMetricsProviderImpl> provider;

  @Inject
  public MetricsServiceImpl(CircuitBreakerMetricsProvider metricsProvider, ActorSystem system) {
    // TODO it would be better to do this in ServiceGuiceSupport.bindServices,
    // but I'm not sure how to access config from there
    boolean statusEnabled = system.settings().config().getBoolean("lagom.status-endpoint.enabled");
    if (statusEnabled && metricsProvider instanceof CircuitBreakerMetricsProviderImpl)
      provider = Optional.of((CircuitBreakerMetricsProviderImpl) metricsProvider);
    else
      provider = Optional.empty();
  }

  @Override
  public ServiceCall<NotUsed, List<CircuitBreakerStatus>> currentCircuitBreakers() {
    return request -> {
      if (!provider.isPresent())
        throw new NotFound("No metrics");
      return CompletableFuture.completedFuture(allCircuitBreakerStatus());
    };
  }
  
  @Override
  public ServiceCall<NotUsed, Source<List<CircuitBreakerStatus>, ?>> circuitBreakers() {
    return request -> {
      if (!provider.isPresent())
        throw new NotFound("No metrics");
      Source<List<CircuitBreakerStatus>, ?> source = 
        Source.tick(FiniteDuration.create(100, TimeUnit.MILLISECONDS), FiniteDuration.create(2, TimeUnit.SECONDS), "tick")
          .map(tick -> allCircuitBreakerStatus());
      return CompletableFuture.completedFuture(source);
    };
  }

  private List<CircuitBreakerStatus> allCircuitBreakerStatus() {
    List<CircuitBreakerStatus> all = new ArrayList<>();
    for (CircuitBreakerMetricsImpl m : provider.get().allMetrics()) {
      try {
        all.add(circuitBreakerStatus(m));
      } catch (Exception e) {
        // might happen if the circuit breaker is removed, just ignore
      }
    }
    return all;
  }

  private CircuitBreakerStatus circuitBreakerStatus(CircuitBreakerMetricsImpl m) {
    Snapshot latencyHistogram = m.latency().getSnapshot();
    Latency latency = Latency.builder()
      .median(latencyHistogram.getMedian())
      .percentile98th(latencyHistogram.get98thPercentile())
      .percentile99th(latencyHistogram.get99thPercentile())
      .percentile999th(latencyHistogram.get999thPercentile())
      .min(latencyHistogram.getMin())
      .max(latencyHistogram.getMax())
      .mean(latencyHistogram.getMean())
      .build();

    return CircuitBreakerStatus.builder().
      id(m.breakerId())
      .state(m.state().getValue())
      .totalSuccessCount(m.successCount().getCount())
      .totalFailureCount(m.failureCount().getCount())
      .throughputOneMinute(m.throughput().getOneMinuteRate())
      .failedThroughputOneMinute(m.failureThroughput().getOneMinuteRate())
      .latencyMicros(latency)
      .build();
  }

}
