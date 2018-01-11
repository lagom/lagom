/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.scaladsl.api.broker.{ TopicFactory, TopicFactoryProvider }
import com.lightbend.lagom.internal.scaladsl.broker.kafka.KafkaTopicFactory
import com.lightbend.lagom.scaladsl.api.{ ServiceInfo, ServiceLocator }

import scala.concurrent.ExecutionContext

trait LagomKafkaClientComponents extends TopicFactoryProvider {
  def serviceInfo: ServiceInfo
  def actorSystem: ActorSystem
  def materializer: Materializer
  def executionContext: ExecutionContext
  def serviceLocator: ServiceLocator

  lazy val topicFactory: TopicFactory = new KafkaTopicFactory(serviceInfo, actorSystem, serviceLocator)(materializer, executionContext)
  override def optionalTopicFactory: Option[TopicFactory] = Some(topicFactory)
}
