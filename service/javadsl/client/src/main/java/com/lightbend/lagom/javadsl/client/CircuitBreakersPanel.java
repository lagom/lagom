/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * A CircuitBreakersPanel is a central point collecting all circuit breakers in Lagom.
 *
 * Calls to remote services can make use of this facility in order to add circuit breaking capabilities to it.
 */
public interface CircuitBreakersPanel {
  <T> CompletionStage<T> withCircuitBreaker(String id, Supplier<CompletionStage<T>> body);
}
