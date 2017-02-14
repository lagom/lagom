/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.internal.broker

import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.broker.{ Subscriber, Topic }
import com.lightbend.lagom.scaladsl.persistence.{ AggregateEvent, AggregateEventTag }

import scala.collection.immutable

trait InternalTopic[Message] extends Topic[Message] {
  final override def topicId: Topic.TopicId = throw new UnsupportedOperationException("Topic#topicId is not permitted in the service's topic implementation")

  final override def subscribe: Subscriber[Message] =
    throw new UnsupportedOperationException("Topic#subscribe is not permitted in the service's topic implementation.")
}

final class TaggedOffsetTopicProducer[Message, Event <: AggregateEvent[Event]](
  val tags:           immutable.Seq[AggregateEventTag[Event]],
  val readSideStream: (AggregateEventTag[Event], Offset) => Source[(Message, Offset), _]
) extends InternalTopic[Message]
