/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jpa;

import javax.persistence.EntityManager;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Provides asynchronous access to a JPA EntityManager. Inject this into classes that require access to JPA, including
 * JPA read-side implementations.
 *
 * @since 1.3
 */
public interface JpaSession {
    /**
     * Execute the given function in a JPA transaction.
     * <p>
     * The JPA EntityManager is provided to the function. This will execute the callback in a thread pool that is
     * specifically designed for use with JDBC calls.
     *
     * @param block the function to execute
     * @return a completion stage that will complete with the result of the supplied block
     */
    <T> CompletionStage<T> withTransaction(Function<EntityManager, T> block);
}
