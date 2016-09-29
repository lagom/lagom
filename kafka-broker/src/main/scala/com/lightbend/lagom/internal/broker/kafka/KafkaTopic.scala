/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import scala.concurrent.ExecutionContext
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Topic
import com.lightbend.lagom.javadsl.api.broker.Subscriber
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.persistence.OffsetStore

/**
 * Represents a Kafka topic and allows publishing/consuming messages to/from the topic.
 */
class KafkaTopic[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message], info: ServiceInfo, system: ActorSystem, offsetStore: OffsetStore)(implicit mat: Materializer, ec: ExecutionContext) extends Topic[Message] {

  override def topicId: TopicId = topicCall.topicId

  override def subscribe(): Subscriber[Message] = Consumer(kafkaConfig, topicCall, info, system)

  private[kafka] def publisher(): Producer[Message] = Producer(kafkaConfig, topicCall, system, offsetStore)
}
