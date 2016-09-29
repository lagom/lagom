/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import scala.concurrent.ExecutionContext
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.internal.api.broker.TopicFactory
import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.Inject

import com.lightbend.lagom.internal.persistence.OffsetStore

/**
 * Factory for creating topics instances.
 */
class KafkaTopicFactory @Inject() (info: ServiceInfo, system: ActorSystem, offsetStore: OffsetStore)(implicit mat: Materializer, ec: ExecutionContext) extends TopicFactory {

  private val kafkaConfig = KafkaConfig(system.settings.config)

  override def create[Message](topicCall: TopicCall[Message]): KafkaTopic[Message] =
    new KafkaTopic[Message](kafkaConfig, topicCall, info, system, offsetStore)
}
