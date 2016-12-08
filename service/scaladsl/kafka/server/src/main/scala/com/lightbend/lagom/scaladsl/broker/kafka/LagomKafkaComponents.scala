/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.broker.kafka

import com.lightbend.lagom.internal.scaladsl.broker.kafka.ScaladslRegisterTopicProducers
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

  // Eagerly start topic producers
  new ScaladslRegisterTopicProducers(lagomServer, topicFactory, serviceInfo, actorSystem,
    offsetStore)(executionContext, materializer)
}
