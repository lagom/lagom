/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.internal.broker

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.broker.Subscriber
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.collection.immutable

trait InternalTopic[BrokerMessage] extends Topic[BrokerMessage] {
  final override def topicId: Topic.TopicId =
    throw new UnsupportedOperationException("Topic#topicId is not permitted in the service's topic implementation")

  final override def subscribe: Subscriber[BrokerMessage] =
    throw new UnsupportedOperationException("Topic#subscribe is not permitted in the service's topic implementation.")
}

trait TaggedInternalTopic[BrokerMessage, Event <: AggregateEvent[Event]] extends InternalTopic[BrokerMessage] {
  val tags: immutable.Seq[AggregateEventTag[Event]]
}

// An InternalTopic used by the legacy TopicProducer API. This creates the Source, maps it to
// Lagom API and also connects it to the user-provided flow in a single shot.
final class TaggedOffsetTopicProducer[BrokerMessage, Event <: AggregateEvent[Event]](
    val tags: immutable.Seq[AggregateEventTag[Event]],
    val readSideStream: (AggregateEventTag[Event], Offset) => Source[(BrokerMessage, Offset), _]
) extends TaggedInternalTopic[BrokerMessage, Event]

/**
 *
 * @param persistentEntityRegistry PersistentEntityRegistry to build the event stream from.
 * @param tags collection of tags to consume from the journal
 * @param clusterShardEntityIds identifier for each entityId (worker actor) when distribution the shards around
 *                              the cluster. This is unnecessary complexity because of the SINGLETON_TAG introduced
 *                              in the legacy implementation for non-sharded tags.
 * @param userFlow flow with user code (and nothing else, no framework code)
 * @tparam BrokerMessage the type published to the broker
 * @tparam Event the type consumed from the journal
 */
// An InternalTopic used by the legacy TopicProducer API. This provides the pieces to create the Source, map it
// to the Lagom API and connect it to the user-provided flow so the ProducerActor can handle that
// and instrument it internally.
final class DelegatedTopicProducer[BrokerMessage, Event <: AggregateEvent[Event]](
    val persistentEntityRegistry: PersistentEntityRegistry,
    val tags: immutable.Seq[AggregateEventTag[Event]],
    val clusterShardEntityIds: immutable.Seq[String],
    userFlow: Flow[EventStreamElement[Event], (BrokerMessage, Offset), NotUsed]
) extends TaggedInternalTopic[BrokerMessage, Event] {
  def userFlowAkka(
      toUserApi: EventEnvelope => EventStreamElement[Event]
  ): Flow[EventEnvelope, (BrokerMessage, Offset), NotUsed] =
    Flow[EventEnvelope]
      .map(toUserApi)
      .via(userFlow)
}
