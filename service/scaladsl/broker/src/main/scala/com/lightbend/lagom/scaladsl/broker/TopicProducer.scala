/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.broker

import akka.NotUsed
import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import com.lightbend.internal.broker.DelegatedTopicProducer
import com.lightbend.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.collection.immutable

/**
 * Creates topic producers.
 *
 * This can be used to help implement [[Topic]] calls on services, a service that returns these topics will
 * automatically have these streams published while the service is running, sharded across the services nodes.
 */
object TopicProducer {

  /**
   * Publish a single stream.
   *
   * This producer will ensure every element from the stream will be published at least once (usually only once),
   * using the message offsets to track where in the stream the producer is up to publishing.
   *
   * @param eventStream A function to create the event stream given the last offset that was published.
   * @return The topic producer.
   */
  def singleStreamWithOffset[Message](eventStream: Offset => Source[(Message, Offset), Any]): Topic[Message] =
    taggedStreamWithOffset(SINGLETON_TAG)((tag, offset) => eventStream(offset))

  private trait SingletonEvent extends AggregateEvent[TopicProducer.SingletonEvent] {}

  // See https://github.com/lagom/lagom/issues/2699 first
  // When the tagger is not sharded, the user provided Flow is already closing on the actual Tag
  // they want to consume, the (classic) TopicProducer API doesn't  allow the user to pass that Tag
  // as an argument. As a consequence, we use a fake SINGLETON_TAG that will be used as a placeholder
  // internally by Lagom but never actually used on a queryByTag.
  // In the DelegatedTopicProducer API (aka .fromTaggedEntity) the user code is not closing on the actual
  // tag so we need to distinguish between what will be used when invoking queryByTag and what will be
  // used as an entityId when sharding the actors.
  private val SINGLETON_TAG = List(AggregateEventTag[TopicProducer.SingletonEvent]("singleton"))

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
  def taggedStreamWithOffset[Message, Event <: AggregateEvent[Event]](tags: immutable.Seq[AggregateEventTag[Event]])(
      eventStream: (AggregateEventTag[Event], Offset) => Source[(Message, Offset), Any]
  ): Topic[Message] =
    new TaggedOffsetTopicProducer[Message, Event](tags, eventStream)

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
  def taggedStreamWithOffset[Message, Event <: AggregateEvent[Event]](shards: AggregateEventShards[Event])(
      eventStream: (AggregateEventTag[Event], Offset) => Source[(Message, Offset), Any]
  ): Topic[Message] =
    new TaggedOffsetTopicProducer[Message, Event](shards.allTags.toList, eventStream)

//  // Requires fixing https://github.com/lagom/lagom/issues/2699 first
//  def fromTaggedEntity[BrokerMessage, Event <: AggregateEvent[Event]](
//                        registry: PersistentEntityRegistry,
//                        tag: AggregateEventTag[Event])(
//                        userFlow: Flow[EventStreamElement[Event], (BrokerMessage, Offset), NotUsed]
//                      ): Topic[BrokerMessage] = {
//    // The legacy implementation used the SINGLETON_TAG while we use the user-provided tag this time.
//    // We need the user provided tag because the invocation to `registry.eventStream` (which now happens on the actor)
//    // will require the user provided tag so we must make sure the internal implementation will continue to
//    // use SINGLETON_TAG for the cluster distribution.
//    new DelegatedTopicProducer(registry, immutable.Seq(tag), "singleton", userFlow)
//  }

  def fromTaggedEntity[BrokerMessage, Event <: AggregateEvent[Event]](
      registry: PersistentEntityRegistry,
      tags: AggregateEventShards[Event]
  )(
      userFlow: Flow[EventStreamElement[Event], (BrokerMessage, Offset), NotUsed]
  ): Topic[BrokerMessage] = {
    val seqTags = tags.allTags.toIndexedSeq
    new DelegatedTopicProducer(registry, seqTags, seqTags.map(_.tag), userFlow)
  }

}
