/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler;

import java.sql.Connection;
import java.sql.SQLException;

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
        ReadSideHandlerBuilder<Event> setGlobalPrepare(ConnectionConsumer callback);

        /**
         * Set a prepare callback.
         *
         * @param callback The callback.
         * @return This builder for fluent invocation.
         * @see ReadSideHandler#prepare(AggregateEventTag)
         */
        ReadSideHandlerBuilder<Event> setPrepare(ConnectionBiConsumer<AggregateEventTag<Event>> callback);

        /**
         * Define the event handler that will be used for events of a given class.
         *
         * @param eventClass The event class to handle.
         * @param handler The function to handle the events.
         * @return This builder for fluent invocation
         */
        <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass, ConnectionBiConsumer<E> handler);

        /**
         * Define the event handler that will be used for events of a given class.
         *
         * This variant allows for offsets to be consumed as well as their events.
         *
         * @param eventClass The event class to handle.
         * @param handler The function to handle the events.
         * @return This builder for fluent invocation
         */
        <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass, ConnectionTriConsumer<E, Offset> handler);

        /**
         * Build the read side handler.
         *
         * @return The read side handler.
         */
        ReadSideHandler<Event> build();
    }

    /**
     * SAM for consuming a connection.
     */
    @FunctionalInterface
    interface ConnectionConsumer {

        /**
         * Accept the connection.
         *
         * @param connection The connection
         */
        void accept(Connection connection) throws SQLException;
    }

    /**
     * SAM for consuming a connection and a parameter
     */
    @FunctionalInterface
    interface ConnectionBiConsumer<T> {

        /**
         * Accept the connection and a parameter.
         *
         * @param connection The connection
         * @param t The first parameter.
         */
        void accept(Connection connection, T t) throws SQLException;
    }

    /**
     * SAM for consuming a connection and two other parameters
     */
    @FunctionalInterface
    interface ConnectionTriConsumer<T, U> {

        /**
         * Accept the connection and two parameters.
         *
         * @param connection The connection
         * @param t The first parameter.
         * @param u The second parameter.
         */
        void accept(Connection connection, T t, U u) throws SQLException;
    }
}
