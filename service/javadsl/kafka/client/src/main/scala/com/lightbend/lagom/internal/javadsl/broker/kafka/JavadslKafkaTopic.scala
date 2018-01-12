/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.{ ServiceInfo, ServiceLocator }
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.api.broker.{ Subscriber, Topic }

import scala.concurrent.ExecutionContext

/**
 * Represents a Kafka topic and allows publishing/consuming messages to/from the topic.
 */
private[lagom] class JavadslKafkaTopic[Payload](kafkaConfig: KafkaConfig, topicCall: TopicCall[Payload],
                                                info: ServiceInfo, system: ActorSystem, serviceLocator: ServiceLocator)(implicit mat: Materializer, ec: ExecutionContext) extends Topic[Payload] {

  override def topicId: TopicId = topicCall.topicId

  override def subscribe(): Subscriber[Payload] = new JavadslKafkaSubscriber[Payload, Payload](kafkaConfig, topicCall,
    JavadslKafkaSubscriber.GroupId.default(info), info, system, serviceLocator, _.value())
}
