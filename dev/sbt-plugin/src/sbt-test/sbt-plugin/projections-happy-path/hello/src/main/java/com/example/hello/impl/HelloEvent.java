/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.hello.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.Value;

public interface HelloEvent extends Jsonable, AggregateEvent<HelloEvent> {
    AggregateEventShards<HelloEvent> TAG = AggregateEventTag.sharded(HelloEvent.class, 3);

    @SuppressWarnings("serial")
    @Value
    @JsonDeserialize
    final class GreetingMessageChanged implements HelloEvent {
        public final String name;
        public final String message;

        @JsonCreator
        GreetingMessageChanged(String name, String message) {
            this.name = Preconditions.checkNotNull(name, "name");
            this.message = Preconditions.checkNotNull(message, "message");
        }
    }

    @Override
    default AggregateEventTagger<HelloEvent> aggregateTag() {
        return TAG;
    }
}
