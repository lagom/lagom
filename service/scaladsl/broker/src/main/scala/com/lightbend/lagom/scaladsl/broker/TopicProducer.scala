/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.broker

import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import com.lightbend.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.scaladsl.api.broker.Message
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag

import scala.collection.immutable

/**
 * Creates topic producers.
 *
 * This can be used to help implement [[Topic]] calls on services, a service that returns these topics will
 * automatically have these streams published while the service is running, sharded across the services nodes.
 */
object TopicProducer {

  private trait SingletonEvent extends AggregateEvent[TopicProducer.SingletonEvent] {}

  private val SINGLETON_TAG = List(AggregateEventTag[TopicProducer.SingletonEvent]("singleton"))

  /**
   * Publish a single stream.
   *
   * This producer will ensure every element from the stream will be published at least once (usually only once),
   * using the message offsets to track where in the stream the producer is up to publishing.
   *
   * @param eventStream A function to create the event stream given the last offset that was published.
   * @return The topic producer.
   */
  def singleStreamWithOffsetAndMetadata[Payload](
      eventStream: Offset => Source[(Message[Payload], Offset), Any]
  ): Topic[Payload] =
    taggedStreamWithOffsetAndMetadata(SINGLETON_TAG)((tag, offset) => eventStream(offset))

  /**
   * Publish a stream that is sharded across many tags.
   *
   * The tags will be distributed around the cluster, ensuring that at most one event stream for each tag is
   * being published at a particular time.
   *
   * This producer will ensure every element from each tags stream will be published at least once (usually only
   * once), using the message offsets to track where in the stream the producer is up to publishing.
   *
   * @param tags        The tags to publish.
   * @param eventStream A function event stream for a given shard given the last offset that was published.
   * @return The topic producer.
   */
  def taggedStreamWithOffsetAndMetadata[Payload, Event <: AggregateEvent[Event]](
      tags: immutable.Seq[AggregateEventTag[Event]]
  )(
      eventStream: (AggregateEventTag[Event], Offset) => Source[(Message[Payload], Offset), Any]
  ): Topic[Payload] =
    TaggedOffsetTopicProducer[Payload, Event](tags, eventStream)

  /**
   * Publish all tags of a stream that is sharded across many tags.
   *
   * The tags will be distributed around the cluster, ensuring that at most one event stream for each tag is
   * being published at a particular time.
   *
   * This producer will ensure every element from each tags stream will be published at least once (usually only
   * once), using the message offsets to track where in the stream the producer is up to publishing.
   *
   * @param shards        The tags to publish.
   * @param eventStream A function event stream for a given shard given the last offset that was published.
   * @return The topic producer.
   */
  def taggedStreamWithOffsetAndMetadata[Payload, Event <: AggregateEvent[Event]](shards: AggregateEventShards[Event])(
      eventStream: (AggregateEventTag[Event], Offset) => Source[(Message[Payload], Offset), Any]
  ): Topic[Payload] =
    TaggedOffsetTopicProducer[Payload, Event](shards.allTags.toList, eventStream)

  /**
   * Publish a single stream.
   *
   * This producer will ensure every element from the stream will be published at least once (usually only once),
   * using the message offsets to track where in the stream the producer is up to publishing.
   *
   * @param eventStream A function to create the event stream given the last offset that was published.
   * @return The topic producer.
   */
  def singleStreamWithOffset[Payload](eventStream: Offset => Source[(Payload, Offset), Any]): Topic[Payload] =
    taggedStreamWithOffset(SINGLETON_TAG)((tag, offset) => eventStream(offset))

  /**
   * Publish a stream that is sharded across many tags.
   *
   * The tags will be distributed around the cluster, ensuring that at most one event stream for each tag is
   * being published at a particular time.
   *
   * This producer will ensure every element from each tags stream will be published at least once (usually only
   * once), using the message offsets to track where in the stream the producer is up to publishing.
   *
   * @param tags        The tags to publish.
   * @param eventStream A function event stream for a given shard given the last offset that was published.
   * @return The topic producer.
   */
  def taggedStreamWithOffset[Payload, Event <: AggregateEvent[Event]](tags: immutable.Seq[AggregateEventTag[Event]])(
      eventStream: (AggregateEventTag[Event], Offset) => Source[(Payload, Offset), Any]
  ): Topic[Payload] =
    TaggedOffsetTopicProducer.withoutMetadata[Payload, Event](tags, eventStream)

  /**
   * Publish all tags of a stream that is sharded across many tags.
   *
   * The tags will be distributed around the cluster, ensuring that at most one event stream for each tag is
   * being published at a particular time.
   *
   * This producer will ensure every element from each tags stream will be published at least once (usually only
   * once), using the message offsets to track where in the stream the producer is up to publishing.
   *
   * @param shards        The tags to publish.
   * @param eventStream A function event stream for a given shard given the last offset that was published.
   * @return The topic producer.
   */
  def taggedStreamWithOffset[Payload, Event <: AggregateEvent[Event]](shards: AggregateEventShards[Event])(
      eventStream: (AggregateEventTag[Event], Offset) => Source[(Payload, Offset), Any]
  ): Topic[Payload] =
    TaggedOffsetTopicProducer
      .withoutMetadata[Payload, Event](shards.allTags.toList, eventStream)
}
