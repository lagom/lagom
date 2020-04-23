/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker

import java.util.function.BiFunction

import akka.japi.Pair
import akka.stream.javadsl.{ Source => JSource }
import com.lightbend.lagom.internal.javadsl.api.InternalTopic
import com.lightbend.lagom.javadsl.api.broker.Message
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.Offset
import org.pcollections.PSequence

final class TaggedOffsetTopicProducer[Payload, Event <: AggregateEvent[Event]](
    val tags: PSequence[AggregateEventTag[Event]],
    val readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[Pair[Message[Payload], Offset], _]]
) extends InternalTopic[Payload]

object TaggedOffsetTopicProducer {

  def withMetadata[Payload, Event <: AggregateEvent[Event]](
      tags: PSequence[AggregateEventTag[Event]],
      readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[Pair[Message[Payload], Offset], _]]
  ): TaggedOffsetTopicProducer[Payload, Event] =
    new TaggedOffsetTopicProducer(tags, readSideStream)

  def withoutMetadata[Payload, Event <: AggregateEvent[Event]](
      tags: PSequence[AggregateEventTag[Event]],
      readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[Pair[Payload, Offset], _]]
  ): TaggedOffsetTopicProducer[Payload, Event] = new TaggedOffsetTopicProducer(
    tags,
    (tag, offset) => readSideStream(tag, offset).map(pair => Pair(Message.create(pair.first), pair.second))
  )

}
