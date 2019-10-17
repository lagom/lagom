/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactoryProvider
import com.lightbend.lagom.internal.scaladsl.broker.kafka.KafkaTopicFactory
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

trait LagomKafkaClientComponents extends TopicFactoryProvider {
  def serviceInfo: ServiceInfo
  def actorSystem: ActorSystem
  def materializer: Materializer
  def executionContext: ExecutionContext
  def serviceLocator: ServiceLocator
  def config: Config

  lazy val topicFactory: TopicFactory =
    new KafkaTopicFactory(serviceInfo, actorSystem, serviceLocator, config) (materializer, executionContext)
  override def optionalTopicFactory: Option[TopicFactory] = Some(topicFactory)
}
