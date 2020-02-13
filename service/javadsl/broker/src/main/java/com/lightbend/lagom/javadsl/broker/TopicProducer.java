/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.broker;

import akka.NotUsed;
import akka.annotation.ApiMayChange;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.internal.broker.DelegatedTopicProducer;
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.persistence.*;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates topic producers.
 *
 * <p>This can be used to help implement {@link Topic} calls on services, a service that returns
 * these topics will automatically have these streams published while the service is running,
 * sharded across the services nodes.
 */
public final class TopicProducer {

  /**
   * Publish a single stream.
   *
   * <p>This producer will ensure every element from the stream will be published at least once
   * (usually only once), using the message offsets to track where in the stream the producer is up
   * to publishing.
   *
   * @param eventStream A function to create the event stream given the last offset that was
   *     published.
   * @return The topic producer.
   */
  public static <Message> Topic<Message> singleStreamWithOffset(
      Function<Offset, Source<Pair<Message, Offset>, ?>> eventStream) {
    return taggedStreamWithOffset(SINGLETON_TAG, (tag, offset) -> eventStream.apply(offset));
  }

  private interface SingletonEvent extends AggregateEvent<SingletonEvent> {}

  // Requires fixing https://github.com/lagom/lagom/issues/2699 first
  // When the tagger is not sharded, the user provided Flow is already closing on the actual Tag
  // they want to consume, the (classic) TopicProducer API doesn't  allow the user to pass that Tag
  // as an argument. As a consequence, we use a fake SINGLETON_TAG that will be used as a
  // placeholder internally by Lagom but never actually used on a queryByTag.  In the
  // DelegatedTopicProducer API (aka .fromTaggedEntity) the user code is not closing on the
  // actual tag so we need to distinguish between what will be used when invoking queryByTag and
  // what will be used as an entityId when sharding the actors.
  private static final PSequence<AggregateEventTag<SingletonEvent>> SINGLETON_TAG =
      TreePVector.singleton(AggregateEventTag.of(SingletonEvent.class, "singleton"));

  /**
   * Publish a stream that is sharded across many tags.
   *
   * <p>The tags will be distributed around the cluster, ensuring that at most one event stream for
   * each tag is being published at a particular time.
   *
   * <p>This producer will ensure every element from each tags stream will be published at least
   * once (usually only once), using the message offsets to track where in the stream the producer
   * is up to publishing.
   *
   * @param tags The tags to publish.
   * @param eventStream A function event stream for a given shard given the last offset that was
   *     published.
   * @return The topic producer.
   */
  public static <Message, Event extends AggregateEvent<Event>>
      Topic<Message> taggedStreamWithOffset(
          PSequence<AggregateEventTag<Event>> tags,
          BiFunction<AggregateEventTag<Event>, Offset, Source<Pair<Message, Offset>, ?>>
              eventStream) {
    return new TaggedOffsetTopicProducer<Message, Event>(tags, eventStream);
  }

  /**
   * Publish all tags of a stream that is sharded across many tags.
   *
   * <p>The tags will be distributed around the cluster, ensuring that at most one event stream for
   * each tag is being published at a particular time.
   *
   * <p>This producer will ensure every element from each tags stream will be published at least
   * once (usually only once), using the message offsets to track where in the stream the producer
   * is up to publishing.
   *
   * @param shards The tags to publish.
   * @param eventStream A function event stream for a given shard given the last offset that was
   *     published.
   * @return The topic producer.
   */
  public static <Message, Event extends AggregateEvent<Event>>
      Topic<Message> taggedStreamWithOffset(
          AggregateEventShards<Event> shards,
          BiFunction<AggregateEventTag<Event>, Offset, Source<Pair<Message, Offset>, ?>>
              eventStream) {
    return new TaggedOffsetTopicProducer<Message, Event>(shards.allTags(), eventStream);
  }

  @ApiMayChange
  public static <Message, Event extends AggregateEvent<Event>> Topic<Message> fromTaggedEntity(
      PersistentEntityRegistry persistentEntityRegistry,
      AggregateEventShards<Event> shards,
      Flow<Pair<Event, Offset>, Pair<Message, Offset>, NotUsed> userFlow) {

    PSequence<String> shardEntityIds =
        TreePVector.from(
            shards.allTags().stream().map(AggregateEventTag::tag).collect(Collectors.toList()));
    return new DelegatedTopicProducer<Message, Event>(
        persistentEntityRegistry, shards.allTags(), shardEntityIds, userFlow);
  }
}
