/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.broker.Subscriber
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId

import scala.concurrent.ExecutionContext

private[lagom] class ScaladslKafkaTopic[Message](
    topicCall: TopicCall[Message],
    info: ServiceInfo,
    system: ActorSystem,
)(implicit mat: Materializer, ec: ExecutionContext)
    extends Topic[Message] {
  override def topicId: TopicId = topicCall.topicId

  override def subscribe: Subscriber[Message] =
    new ScaladslKafkaSubscriber[Message, Message](
      topicCall,
      ScaladslKafkaSubscriber.GroupId.default(info),
      info,
      system,
      _.value
    )
}
