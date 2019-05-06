/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactory
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

/**
 * Factory for creating topics instances.
 */
private[lagom] class KafkaTopicFactory(
    serviceInfo: ServiceInfo,
    system: ActorSystem,
    serviceLocator: ServiceLocator,
    config: Config
)(implicit materializer: Materializer, executionContext: ExecutionContext)
    extends TopicFactory {

  @deprecated("Use constructor that accepts a Config", "2.0.0")
  def this(serviceInfo: ServiceInfo, system: ActorSystem, serviceLocator: ServiceLocator)(
      implicit materializer: Materializer,
      executionContext: ExecutionContext
  ) = {
    this(serviceInfo, system, serviceLocator, system.settings.config)
  }

  private val kafkaConfig = KafkaConfig(config)

  def create[Message](topicCall: TopicCall[Message]): Topic[Message] = {
    new ScaladslKafkaTopic(kafkaConfig, topicCall, serviceInfo, system, serviceLocator)
  }
}
