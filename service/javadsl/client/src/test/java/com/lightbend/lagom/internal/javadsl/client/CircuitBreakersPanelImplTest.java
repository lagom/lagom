/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.client;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.lightbend.lagom.internal.client.CircuitBreakerConfig;
import com.lightbend.lagom.internal.spi.CircuitBreakerMetrics;
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CircuitBreakersPanelImplTest {

  static ActorSystem system;
  static CircuitBreakerMetrics metrics;
  static CircuitBreakersPanelImpl circuitBreakersPanel;

  @BeforeClass
  public static void setup() {
    Config conf =
        ConfigFactory.parseString(
            "lagom.circuit-breaker.default {\n"
                + "    max-failures = 1\n"
                + "    exception-whitelist = [ \""
                + FakeException.class.getName()
                + "\" ]\n"
                + "    enabled = on\n"
                + "    call-timeout = 10s\n"
                + "    reset-timeout = 15s\n"
                + "}\n");
    system = ActorSystem.create("CircuitBreakersPanelImplTest", conf);
    CircuitBreakerConfig cbConfig = new CircuitBreakerConfig(conf);
    metrics = mock(CircuitBreakerMetrics.class, "metrics");
    CircuitBreakerMetricsProvider metricsProvider = mock(CircuitBreakerMetricsProvider.class);
    when(metricsProvider.start(anyString())).thenReturn(metrics);
    circuitBreakersPanel = new CircuitBreakersPanelImpl(system, cbConfig, metricsProvider);
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Before
  public void before() {
    reset(metrics);
  }

  private CompletionStage<Object> throwException(RuntimeException ex) {
    return completedFuture(null)
        .thenApply(
            x -> {
              throw ex;
            });
  }

  @Test
  public void testSuccessBlock() throws Exception {
    circuitBreakersPanel
        .withCircuitBreaker("ignored", () -> completedFuture(null))
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);
    verify(metrics).onCallSuccess(anyLong());
    verify(metrics, never()).onOpen();
    verify(metrics, never()).onClose();
    verify(metrics, never()).onHalfOpen();
  }

  @Test
  public void testBlockWithIgnoredException() throws Exception {
    circuitBreakersPanel
        .withCircuitBreaker("ignored", () -> this.throwException(new FakeException()))
        .exceptionally(Throwable::getMessage)
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);
    verify(metrics).onCallSuccess(anyLong());
    verify(metrics, never()).onOpen();
    verify(metrics, never()).onClose();
    verify(metrics, never()).onHalfOpen();
  }

  @Test
  public void testBlockWithException() throws Exception {
    circuitBreakersPanel
        .withCircuitBreaker("checked", () -> this.throwException(new RuntimeException("")))
        .exceptionally(Throwable::getMessage)
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);
    verify(metrics).onCallFailure(anyLong());
    verify(metrics).onOpen();
    verify(metrics, never()).onClose();
    verify(metrics, never()).onHalfOpen();
  }

  static final class FakeException extends RuntimeException {}
}
