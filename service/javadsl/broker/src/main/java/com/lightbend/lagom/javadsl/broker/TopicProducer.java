/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.broker;

import akka.japi.Pair;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Creates topic producers.
 *
 * This can be used to help implement {@link Topic} calls on services, a service that returns these topics will
 * automatically have these streams published while the service is running, sharded across the services nodes.
 */
public final class TopicProducer {

    /**
     * Publish a single stream.
     *
     * This producer will ensure every element from the stream will be published at least once (usually only once),
     * using the message offsets to track where in the stream the producer is up to publishing.
     *
     * @param eventStream A function to create the event stream given the last offset that was published.
     * @return The topic producer.
     */
    public static <Message> Topic<Message> singleStreamWithOffset(
            Function<Offset, Source<Pair<Message, Offset>, ?>> eventStream) {
        return taggedStreamWithOffset(SINGLETON_TAG, (tag, offset) -> eventStream.apply(offset));
    }

    private interface SingletonEvent extends AggregateEvent<SingletonEvent> {}
    private static final PSequence<AggregateEventTag<SingletonEvent>> SINGLETON_TAG = TreePVector.singleton(
            AggregateEventTag.of(SingletonEvent.class, "singleton")
    );

    /**
     * Publish a stream that is sharded across many tags.
     *
     * The tags will be distributed around the cluster, ensuring that at most one event stream for each tag is
     * being published at a particular time.
     *
     * This producer will ensure every element from each tags stream will be published at least once (usually only
     * once), using the message offsets to track where in the stream the producer is up to publishing.
     *
     * @param tags The tags to publish.
     * @param eventStream A function event stream for a given shard given the last offset that was published.
     * @return The topic producer.
     */
    public static <Message, Event extends AggregateEvent<Event>> Topic<Message> taggedStreamWithOffset(
            PSequence<AggregateEventTag<Event>> tags,
            BiFunction<AggregateEventTag<Event>, Offset, Source<Pair<Message, Offset>, ?>> eventStream) {
        return new TaggedOffsetTopicProducer<>(tags, eventStream);
    }


}
