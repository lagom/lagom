/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker

import java.util.Optional
import java.util.function.BiFunction

import akka.japi.Pair
import akka.stream.javadsl.{ Source => JSource }
import com.lightbend.lagom.internal.javadsl.api.InternalTopic
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.Offset
import org.pcollections.PSequence

final class TaggedOffsetTopicProducer[Message, Event <: AggregateEvent[Event]](
    val tags: PSequence[AggregateEventTag[Event]],
    val readSideStream: BiFunction[AggregateEventTag[Event], Offset, JSource[Pair[Optional[Message], Offset], _]]
) extends InternalTopic[Message]
