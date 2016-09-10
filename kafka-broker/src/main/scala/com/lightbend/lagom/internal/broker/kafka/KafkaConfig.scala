/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import javax.inject.Inject
import play.api.Configuration
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

trait KafkaConfig {
  def brokers: String
  def consumerBatchingSize: Int
  def consumerBatchingInterval: FiniteDuration
}

object KafkaConfig {
  class ConfigImpl @Inject() (config: Configuration) extends KafkaConfig {
    override val brokers: String = config.underlying.getString("lagom.broker.kafka.brokers")
    override val consumerBatchingSize: Int = config.underlying.getInt("lagom.broker.kafka.consumer.batching-size")
    override val consumerBatchingInterval: FiniteDuration = {
      val interval = config.underlying.getDuration("lagom.broker.kafka.consumer.batching-interval")
      FiniteDuration(interval.toMillis(), TimeUnit.MILLISECONDS)
    }
  }
}
