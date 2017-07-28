/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client;

import akka.actor.ActorSystem;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.lightbend.lagom.internal.client.CircuitBreakerConfig;
import com.lightbend.lagom.internal.client.CircuitBreakersPanelInternal;
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider;
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.JFunction0;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction0;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@Singleton
public class CircuitBreakersPanelImpl implements CircuitBreakersPanel {

  private final CircuitBreakersPanelInternal circuitBreakersPanelInternal;

  @Inject()
  public CircuitBreakersPanelImpl(ActorSystem system,
                                  CircuitBreakerConfig config,
                                  CircuitBreakerMetricsProvider metricsProvider) {

    this(new CircuitBreakersPanelInternal(system, config, metricsProvider));
  }

  public CircuitBreakersPanelImpl(CircuitBreakersPanelInternal circuitBreakersPanelInternal) {
    this.circuitBreakersPanelInternal = circuitBreakersPanelInternal;
  }

  @Override
  public <T> CompletionStage<T> withCircuitBreaker(String id, Supplier<CompletionStage<T>> body) {

    return FutureConverters.toJava(
            circuitBreakersPanelInternal.withCircuitBreaker(id,
                    (JFunction0<Future<T>>) () -> FutureConverters.toScala(body.get()))
    );
  }
}
