/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jpa;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.jdbc.EventLogEntityType;

import javax.persistence.EntityManager;

/**
 * Exposes the event log for direct insertion of events within an existing JPA transaction.
 * <p>
 * This is provided to allow standard CRUD operations to be complemented with an event log, allowing events to be
 * published atomically with the CRUD operations, which then allows read sides and topics to be published with
 * guaranteed eventual consistency.
 * <p>
 * This must not be used to publish events to persistent entities that have been registered with the
 * {@link com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry}. Doing so will undermine the persistent
 * entities and result in failures.
 */
public interface JpaEventLog {

  /**
   * Create an entity event log for the given entity type.
   *
   * @param entityType The entity type.
   * @return An entity event log for the given type of entity.
   */
  <Event extends AggregateEvent<Event>> EntityEventLog<Event> eventLogFor(Class<? extends EventLogEntityType<Event>> entityType);

  /**
   * An event log for a specific entity type.
   */
  interface EntityEventLog<Event extends AggregateEvent<Event>> {

    /**
     * Emit an event for the entity with the given id in the transaction associated with the passed in entity manager.
     *
     * @param em The entity manager.
     * @param entityId The id of the entity to emit an event for.
     * @param event The event to emit.
     */
    void emit(EntityManager em, String entityId, Event event);
  }
}
