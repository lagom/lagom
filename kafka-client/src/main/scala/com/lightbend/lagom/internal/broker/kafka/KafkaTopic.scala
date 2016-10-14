/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.{ Subscriber, Topic }
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import scala.concurrent.ExecutionContext

/**
 * Represents a Kafka topic and allows publishing/consuming messages to/from the topic.
 */
class KafkaTopic[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message], info: ServiceInfo, system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) extends Topic[Message] {

  override def topicId: TopicId = topicCall.topicId

  override def subscribe(): Subscriber[Message] = Consumer(kafkaConfig, topicCall, info, system)
}
