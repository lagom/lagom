/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc;

import akka.event.Logging;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;

/**
 * Entity type descriptor for a complementary event log.
 *
 * @param <Event>
 */
public interface EventLogEntityType<Event extends AggregateEvent<Event>> {
  /**
   * The name of the entity type.
   */
  default String entityTypeName() {
    return Logging.simpleName(this);
  }
}
