/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.internal.broker

import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.broker.Subscriber
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducerCommand
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag

import scala.collection.immutable

trait InternalTopic[Message] extends Topic[Message] {
  final override def topicId: Topic.TopicId =
    throw new UnsupportedOperationException("Topic#topicId is not permitted in the service's topic implementation")

  final override def subscribe: Subscriber[Message] =
    throw new UnsupportedOperationException("Topic#subscribe is not permitted in the service's topic implementation.")
}

final class TaggedOffsetTopicProducer[Message, Event <: AggregateEvent[Event]](
    val tags: immutable.Seq[AggregateEventTag[Event]],
    val readSideStream: (AggregateEventTag[Event], Offset) => Source[TopicProducerCommand[Message], _]
) extends InternalTopic[Message]

object TaggedOffsetTopicProducer {

  def fromTopicProducerCommandStream[Message, Event <: AggregateEvent[Event]](
      tags: immutable.Seq[AggregateEventTag[Event]],
      readSideStream: (AggregateEventTag[Event], Offset) => Source[TopicProducerCommand[Message], _]
  ): TaggedOffsetTopicProducer[Message, Event] = new TaggedOffsetTopicProducer[Message, Event](tags, readSideStream)

  def fromEventAndOffsetPairStream[Message, Event <: AggregateEvent[Event]](
      tags: immutable.Seq[AggregateEventTag[Event]],
      readSideStream: (AggregateEventTag[Event], Offset) => Source[(Message, Offset), _]
  ): TaggedOffsetTopicProducer[Message, Event] =
    new TaggedOffsetTopicProducer[Message, Event](
      tags,
      (tag, offset) =>
        readSideStream.apply(tag, offset).map(pair => new TopicProducerCommand.EmitAndCommit(pair._1, pair._2))
    )

}
