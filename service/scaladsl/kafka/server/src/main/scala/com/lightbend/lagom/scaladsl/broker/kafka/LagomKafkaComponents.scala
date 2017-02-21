/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.broker.kafka

import com.lightbend.lagom.internal.scaladsl.broker.kafka.ScaladslRegisterTopicProducers
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.spi.persistence.OffsetStore

/**
 * Components for including Kafka into a Lagom application.
 *
 * Extending this trait will automatically start all topic producers.
 */
trait LagomKafkaComponents extends LagomKafkaClientComponents {
  def lagomServer: LagomServer
  def offsetStore: OffsetStore
  def serviceLocator: ServiceLocator

  override def topicPublisherName: Option[String] = super.topicPublisherName match {
    case Some(other) =>
      sys.error(s"Cannot provide the kafka topic factory as the default topic publisher since a default topic publisher has already been mixed into this cake: $other")
    case None => Some("kafka")
  }

  // Eagerly start topic producers
  new ScaladslRegisterTopicProducers(lagomServer, topicFactory, serviceInfo, actorSystem,
    offsetStore, serviceLocator)(executionContext, materializer)
}
