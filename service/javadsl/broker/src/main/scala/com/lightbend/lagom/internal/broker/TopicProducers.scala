/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker

import java.util.function.BiFunction

import akka.japi.Pair
import akka.stream.javadsl.{ Source => JSource }
import com.lightbend.lagom.internal.javadsl.api.InternalTopic
import com.lightbend.lagom.javadsl.broker.TopicProducerCommand
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.Offset
import org.pcollections.PSequence

final class TaggedOffsetTopicProducer[Message, Event <: AggregateEvent[Event]](
    val tags: PSequence[AggregateEventTag[Event]],
    val readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[TopicProducerCommand[Message], _]]
) extends InternalTopic[Message]

object TaggedOffsetTopicProducer {

  def fromTopicProducerCommandStream[Message, Event <: AggregateEvent[Event]](
      tags: PSequence[AggregateEventTag[Event]],
      readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[TopicProducerCommand[Message], _]]
  ): TaggedOffsetTopicProducer[Message, Event] = new TaggedOffsetTopicProducer[Message, Event](tags, readSideStream)

  def fromEventAndOffsetPairStream[Message, Event <: AggregateEvent[Event]](
      tags: PSequence[AggregateEventTag[Event]],
      readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[Pair[Message, Offset], _]]
  ): TaggedOffsetTopicProducer[Message, Event] =
    new TaggedOffsetTopicProducer[Message, Event](
      tags,
      (tag, offset) =>
        readSideStream.apply(tag, offset).map(pair => new TopicProducerCommand.EmitAndCommit(pair.first, pair.second))
    )

}
