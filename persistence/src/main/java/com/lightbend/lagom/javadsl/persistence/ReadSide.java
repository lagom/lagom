/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

public interface ReadSide {

    <Event extends AggregateEvent<Event>> void register(Class<? extends ReadSideProcessor<Event>> processorClass);

}
