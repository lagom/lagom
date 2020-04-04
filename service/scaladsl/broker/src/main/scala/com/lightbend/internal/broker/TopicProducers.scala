/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.internal.broker

import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.api.broker.MessageWithMetadata
import com.lightbend.lagom.scaladsl.api.broker.Message
import com.lightbend.lagom.scaladsl.api.broker.Subscriber
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag

import scala.collection.immutable

trait InternalTopic[Payload] extends Topic[Payload] {
  final override def topicId: Topic.TopicId =
    throw new UnsupportedOperationException("Topic#topicId is not permitted in the service's topic implementation")

  final override def subscribe: Subscriber[Payload] =
    throw new UnsupportedOperationException("Topic#subscribe is not permitted in the service's topic implementation.")
}

final class TaggedOffsetTopicProducer[Payload, Event <: AggregateEvent[Event]](
    val tags: immutable.Seq[AggregateEventTag[Event]],
    val readSideStream: (AggregateEventTag[Event], Offset) => Source[(MessageWithMetadata[Payload], Offset), _]
) extends InternalTopic[Payload]

object TaggedOffsetTopicProducer {

  def apply[Payload, Event <: AggregateEvent[Event]](
      tags: immutable.Seq[AggregateEventTag[Event]],
      readSideStream: (AggregateEventTag[Event], Offset) => Source[(MessageWithMetadata[Payload], Offset), _]
  ): TaggedOffsetTopicProducer[Payload, Event] =
    new TaggedOffsetTopicProducer(tags, readSideStream)

  def withoutMetadata[Payload, Event <: AggregateEvent[Event]](
      tags: immutable.Seq[AggregateEventTag[Event]],
      readSideStream: (AggregateEventTag[Event], Offset) => Source[(Payload, Offset), _]
  ): TaggedOffsetTopicProducer[Payload, Event] = new TaggedOffsetTopicProducer(
    tags,
    (tag, offset) =>
      readSideStream(tag, offset).map {
        case (payload, newOffset) => (Message(payload), newOffset)
      }
  )

}
