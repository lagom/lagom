/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence;

import akka.annotation.ApiMayChange;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

@ApiMayChange
public class AkkaTaggerAdapter {

  /**
   * Adapts an existing Lagom {@code AggregateEventTagger} to a function {@code Function<Event,
   * Set<String>>} as expected by Akka Persistence Typed {@code EventSourcedBehavior.withTagger}
   * API.
   */
  public static <Command, Event extends AggregateEvent<Event>>
      Function<Event, Set<String>> fromLagom(
          EntityContext<Command> entityContext, AggregateEventTagger<Event> lagomTagger) {
    return evt -> {
      Set<String> tags = new HashSet<>();
      if (lagomTagger instanceof AggregateEventTag) {
        tags.add(((AggregateEventTag) lagomTagger).tag());
      } else if (lagomTagger instanceof AggregateEventShards) {
        tags.add(
            ((AggregateEventShards) lagomTagger).forEntityId(entityContext.getEntityId()).tag());
      }
      return tags;
    };
  }
}
