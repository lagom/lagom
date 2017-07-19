/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * A CircuitBreakersPanel is a central point collecting all circuit breakers in Lagom.
 * <p>
 * Calls to remote services can make use of this facility in order to add circuit breaking capabilities to it.
 */
public interface CircuitBreakersPanel {
    /**
     * Executes {@code body} in the context of the circuit breaker identified by {@code id}. Whether {@code body.get()}
     * is actually invoked is implementation-dependent, but implementations should call it at most once.
     *
     * @param id   the unique identifier for the circuit breaker to use (often a service name)
     * @param body effect to (optionally) execute within the context of the circuit breaker. May throw a
     *             {@code RuntimeException} to signal failure.
     * @param <T>  the result type
     * @return a completion stage yielding either the same result as {@code body.get()}, or failing with an
     * implementation-dependent exception.
     */
    <T> CompletionStage<T> withCircuitBreaker(String id, Supplier<CompletionStage<T>> body);
}
