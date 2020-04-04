/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.broker.Subscriber
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId

import scala.concurrent.ExecutionContext

private[lagom] class ScaladslKafkaTopic[Payload](
    kafkaConfig: KafkaConfig,
    topicCall: TopicCall[Payload],
    info: ServiceInfo,
    system: ActorSystem,
    serviceLocator: ServiceLocator
)(implicit mat: Materializer, ec: ExecutionContext)
    extends Topic[Payload] {
  override def topicId: TopicId = topicCall.topicId

  override def subscribe: Subscriber[Payload] =
    new ScaladslKafkaSubscriber[Payload, Payload](
      kafkaConfig,
      topicCall,
      ScaladslKafkaSubscriber.GroupId.default(info),
      info,
      system,
      serviceLocator,
      _.value
    )
}
