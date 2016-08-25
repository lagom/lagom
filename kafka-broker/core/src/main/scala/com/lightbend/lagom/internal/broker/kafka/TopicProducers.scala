/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.util.function.Function

import com.lightbend.lagom.internal.api.InternalTopic
import com.lightbend.lagom.javadsl.api.broker.Topic
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.persistence.Offset

import akka.NotUsed
import akka.japi.Pair
import akka.stream.javadsl.{ Source => JSource }

final class SingletonTopicProducer[Message] private (
  val topicId:        TopicId,
  val readSideStream: Function[Offset, JSource[Pair[Message, Offset], NotUsed]]
) extends InternalTopic[Message] {

  def this(readSideStream: Function[Offset, JSource[Pair[Message, Offset], NotUsed]]) = this(TopicId.of("__unresolved__"), readSideStream)

  override def withTopicId(topicId: Topic.TopicId): Topic[Message] = new SingletonTopicProducer(topicId, readSideStream)
}
