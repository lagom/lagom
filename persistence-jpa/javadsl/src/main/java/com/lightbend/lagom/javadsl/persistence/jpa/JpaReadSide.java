/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jpa;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler;

import javax.persistence.EntityManager;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * JPA read side support.
 *
 * This should be used to build and register a read side processor.
 *
 * All callbacks are executed in a transaction and are automatically committed
 * or rolled back based on whether they fail or succeed.
 *
 * Offsets are automatically handled.
 *
 * @since 1.3
 */
public interface JpaReadSide {

    /**
     * Create a builder for a JPA read side event handler.
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
        ReadSideHandlerBuilder<Event> setGlobalPrepare(Consumer<EntityManager> callback);

        /**
         * Set a prepare callback.
         *
         * @param callback The callback.
         * @return This builder for fluent invocation.
         * @see ReadSideHandler#prepare(AggregateEventTag)
         */
        ReadSideHandlerBuilder<Event> setPrepare(BiConsumer<EntityManager, AggregateEventTag<Event>> callback);

        /**
         * Define the event handler that will be used for events of a given class.
         *
         * @param eventClass The event class to handle.
         * @param handler    The function to handle the events.
         * @return This builder for fluent invocation
         */
        <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass,
                                                                        BiConsumer<EntityManager, E> handler);

        /**
         * Build the read side handler.
         *
         * @return The read side handler.
         */
        ReadSideHandler<Event> build();
    }
}
