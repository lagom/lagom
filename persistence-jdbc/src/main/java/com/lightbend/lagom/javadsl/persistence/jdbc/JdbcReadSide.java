/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler;

import java.sql.Connection;
import java.util.function.*;

/**
 * JDBC read side support.
 *
 * This should be used to build and register a read side processor.
 *
 * All callbacks are executed in a transaction and are automatically committed or rollback based on whether they fail
 * or succeed.
 *
 * Offsets are automatically handled.
 */
public interface JdbcReadSide {

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
        ReadSideHandlerBuilder<Event> setGlobalPrepare(Consumer<Connection> callback);

        /**
         * Set a prepare callback.
         *
         * @param callback The callback.
         * @return This builder for fluent invocation.
         * @see ReadSideHandler#prepare(AggregateEventTag)
         */
        ReadSideHandlerBuilder<Event> setPrepare(BiConsumer<Connection, AggregateEventTag<Event>> callback);

        /**
         * Define the event handler that will be used for events of a given class.
         *
         * @param eventClass The event class to handle.
         * @param handler The function to handle the events.
         * @return This builder for fluent invocation
         */
        <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass, BiConsumer<Connection, E> handler);

        /**
         * Define the event handler that will be used for events of a given class.
         *
         * This variant allows for offsets to be consumed as well as their events.
         *
         * @param eventClass The event class to handle.
         * @param handler The function to handle the events.
         * @return This builder for fluent invocation
         */
        <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass, OffsetConsumer<E> handler);

        /**
         * Build the read side handler.
         *
         * @return The read side handler.
         */
        ReadSideHandler<Event> build();
    }

    /**
     * SAM for handling offsets.
     */
    @FunctionalInterface
    interface OffsetConsumer<E> {

        /**
         * Accept the connection, event and offset, and handle it.
         *
         * @param connection The connection
         * @param event The event
         * @param offset The offset
         */
        void accept(Connection connection, E event, Offset offset);
    }
}
