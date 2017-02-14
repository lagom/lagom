/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

/**
 * The Lagom read-side registry.
 *
 * Handles the management of read-sides.
 */
public interface ReadSide {

    /**
     * Register a read-side processor with Lagom.
     *
     * @param processorClass The read-side processor class to register. It will be instantiated using Guice, once for
     *                       every shard that runs it. Typically it should not be a singleton.
     */
    <Event extends AggregateEvent<Event>> void register(Class<? extends ReadSideProcessor<Event>> processorClass);

}
