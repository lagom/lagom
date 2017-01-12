/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.api.broker.{ Subscriber, Topic }

import scala.concurrent.ExecutionContext

/**
 * Represents a Kafka topic and allows publishing/consuming messages to/from the topic.
 */
private[lagom] class JavadslKafkaTopic[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message], info: ServiceInfo, system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) extends Topic[Message] {

  override def topicId: TopicId = topicCall.topicId

  override def subscribe(): Subscriber[Message] = new JavadslKafkaSubscriber(kafkaConfig, topicCall,
    JavadslKafkaSubscriber.GroupId.default(info), info, system)
}
