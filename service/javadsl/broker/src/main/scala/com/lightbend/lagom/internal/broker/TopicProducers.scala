/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker

import java.util.function.BiFunction

import akka.NotUsed
import akka.japi.Pair
import akka.stream.javadsl.Flow
import akka.stream.javadsl.{Source => JSource}
import com.lightbend.lagom.internal.javadsl.api.InternalTopic
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import org.pcollections.PSequence

import scala.collection.immutable

trait TaggedInternalTopic[BrokerMessage, Event <: AggregateEvent[Event]] extends InternalTopic[BrokerMessage] {
  val tags: PSequence[AggregateEventTag[Event]]
}

// An InternalTopic used by the legacy TopicProducer API. This creates the Source, maps it to
// Lagom API and also connects it to the user-provided flow in a single shot.
final class TaggedOffsetTopicProducer[BrokerMessage, Event <: AggregateEvent[Event]](
    val tags: PSequence[AggregateEventTag[Event]],
    val readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[Pair[BrokerMessage, Offset], _]]
) extends TaggedInternalTopic[BrokerMessage, Event]

// An InternalTopic used by the legacy TopicProducer API. This provides the pieces to create the Source, map it
// to the Lagom API and connect it to the user-provided flow so the ProducerActor can handle that
// and instrument it internally.
final class DelegatedTopicProducer[BrokerMessage, Event <: AggregateEvent[Event]](
    val persistentEntityRegistry: PersistentEntityRegistry,
    val tags: PSequence[AggregateEventTag[Event]],
    val clusterShardEntityIds: immutable.Seq[AggregateEventTag[_]],
    val userFlow: Flow[Pair[Event, Offset], Pair[BrokerMessage, Offset], NotUsed]
) extends TaggedInternalTopic[BrokerMessage, Event]
