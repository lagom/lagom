/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactory
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.broker.Topic

import scala.concurrent.ExecutionContext

/**
 * Factory for creating topics instances.
 */
private[lagom] class KafkaTopicFactory(serviceInfo: ServiceInfo, system: ActorSystem)(implicit materializer: Materializer, executionContext: ExecutionContext) extends TopicFactory {

  private val config = KafkaConfig(system.settings.config)

  def create[Message](topicCall: TopicCall[Message]): Topic[Message] = {
    new ScaladslKafkaTopic(config, topicCall, serviceInfo, system)
  }
}
