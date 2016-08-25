/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker
import com.lightbend.lagom.javadsl.api.broker.Publisher
import com.lightbend.lagom.javadsl.api.broker.Subscriber
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId

import akka.actor.ActorSystem
import akka.stream.Materializer

/**
 * Represents a Kafka topic and allows publishing/consuming messages to/from the topic.  
 */
class Topic[Message](config: KafkaConfig, topicCall: TopicCall[Message], info: ServiceInfo, system: ActorSystem, mat: Materializer) extends broker.Topic[Message] {

  override def topicId: TopicId = topicCall.topicId

  override def subscribe(): Subscriber[Message] = Consumer(config, topicCall, info, system, mat)

  override def publisher(): Publisher[Message] = Producer(config, topicCall, system, mat)
}
