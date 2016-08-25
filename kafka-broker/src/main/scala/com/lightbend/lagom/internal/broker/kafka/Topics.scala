/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.modules

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.Inject

/**
 * Factory for creating topics instances.
 */
class Topics @Inject() (config: KafkaConfig, info: ServiceInfo, system: ActorSystem, mat: Materializer) extends modules.Topics {
  override def of[Message](topicCall: TopicCall[Message]): Topic[Message] =
    new Topic[Message](config, topicCall, info, system, mat)
}
