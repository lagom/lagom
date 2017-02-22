/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.{ ServiceInfo, ServiceLocator }
import com.lightbend.lagom.scaladsl.api.broker.{ Subscriber, Topic }
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId

import scala.concurrent.ExecutionContext

private[lagom] class ScaladslKafkaTopic[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message],
                                                 info: ServiceInfo, system: ActorSystem, serviceLocator: ServiceLocator)(implicit mat: Materializer, ec: ExecutionContext) extends Topic[Message] {

  override def topicId: TopicId = topicCall.topicId

  override def subscribe: Subscriber[Message] = new ScaladslKafkaSubscriber(kafkaConfig, topicCall,
    ScaladslKafkaSubscriber.GroupId.default(info), info, system, serviceLocator)
}
