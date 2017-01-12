/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra;

import akka.Done;
import com.datastax.driver.core.BoundStatement;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSideProcessor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Cassandra read side support.
 *
 * This should be used to build and register readside
 */
public interface CassandraReadSide {
    /**
     * At system startup all {@link CassandraReadSideProcessor} classes must be registered
     * with this method.
     *
     * @deprecated Use the builder method to create and register a Cassandra read side processor.
     */
    @Deprecated
    <Event extends AggregateEvent<Event>> void register(Class<? extends CassandraReadSideProcessor<Event>> processorClass);

    /**
     * Create a builder for a Cassandra read side event handler.
     *
     * @param readSideId An identifier for this read side. This will be used to store offsets in the offset store.
     * @return The builder.
     */
    <Event extends AggregateEvent<Event>> ReadSideHandlerBuilder<Event> builder(String readSideId);

    /**
     * Builder for the handler.
     */
    interface ReadSideHandlerBuilder<Event extends AggregateEvent<Event>> {

        /**
         * Set a global prepare callback.
         *
         * @param callback The callback.
         * @return This builder for fluent invocation.
         * @see ReadSideHandler#globalPrepare()
         */
        ReadSideHandlerBuilder<Event> setGlobalPrepare(Supplier<CompletionStage<Done>> callback);

        /**
         * Set a prepare callback.
         *
         * @param callback The callback.
         * @return This builder for fluent invocation.
         * @see ReadSideHandler#prepare(AggregateEventTag)
         */
        ReadSideHandlerBuilder<Event> setPrepare(Function<AggregateEventTag<Event>, CompletionStage<Done>> callback);

        /**
         * Define the event handler that will be used for events of a given class.
         *
         * @param eventClass The event class to handle.
         * @param handler The function to handle the events.
         * @return This builder for fluent invocation
         */
        <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass, Function<E, CompletionStage<List<BoundStatement>>> handler);

        /**
         * Define the event handler that will be used for events of a given class.
         *
         * This variant allows for offsets to be consumed as well as their events.
         *
         * @param eventClass The event class to handle.
         * @param handler The function to handle the events.
         * @return This builder for fluent invocation
         */
        <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass, BiFunction<E, Offset, CompletionStage<List<BoundStatement>>> handler);

        /**
         * Build the read side handler.
         *
         * @return The read side handler.
         */
        ReadSideHandler<Event> build();
    }

    /**
     * Convenience method to create an already completed <code>CompletionStage</code> with one <code>BoundStatement</code>.
     */
    static CompletionStage<List<BoundStatement>> completedStatement(BoundStatement statement) {
        return CompletableFuture.completedFuture(Collections.singletonList(statement));
    }

    /**
     * Convenience method to create an already completed <code>CompletionStage</code> with several <code>BoundStatement</code>.
     */
    static CompletionStage<List<BoundStatement>> completedStatements(List<BoundStatement> statements) {
        return CompletableFuture.completedFuture(statements);
    }

    /**
     * Convenience method to create an already completed <code>CompletionStage</code> with no <code>BoundStatement</code>.
     */
    static CompletionStage<List<BoundStatement>> completedStatements() {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
