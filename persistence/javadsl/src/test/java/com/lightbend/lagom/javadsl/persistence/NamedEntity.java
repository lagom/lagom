/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.serialization.Jsonable;

import javax.inject.Inject;
import java.util.Optional;

public class NamedEntity extends PersistentEntity<NamedEntity.Cmd, NamedEntity.Evt, NamedEntity.State> {

    @Override
    public String entityTypeName() {
        return "some-name";
    }

    public static interface Cmd extends Jsonable {
    }

    public static abstract class Evt implements AggregateEvent<Evt>, Jsonable {
        private static final long serialVersionUID = 1L;
        public static final AggregateEventTag<Evt> AGGREGATE_EVENT_SHARDS = AggregateEventTag.of(Evt.class);

        @Override
        public AggregateEventTagger<Evt> aggregateTag() {
            return AGGREGATE_EVENT_SHARDS;
        }
    }

    public static class State implements Jsonable {
        private static final long serialVersionUID = 1L;

        @JsonCreator
        public State() {
        }
    }

    @Inject
    public NamedEntity() {
    }

    @Override
    public Behavior initialBehavior(Optional<State> snapshotState) {
        BehaviorBuilder b = newBehaviorBuilder(new State());
        return b.build();
    }

}
