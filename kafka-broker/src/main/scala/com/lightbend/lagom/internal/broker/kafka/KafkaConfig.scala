/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import javax.inject.Inject
import play.api.Configuration

trait KafkaConfig {
  def brokers: String
}

object KafkaConfig {
  class ConfigImpl @Inject() (config: Configuration) extends KafkaConfig {
    override val brokers: String = config.underlying.getString("lagom.broker.kafka.brokers")
  }
}
