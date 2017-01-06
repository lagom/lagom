/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.{ FiniteDuration, _ }

sealed trait KafkaConfig {
  def brokers: String
}

object KafkaConfig {
  def apply(conf: Config): KafkaConfig =
    new KafkaConfigImpl(conf.getConfig("lagom.broker.kafka"))

  private final class KafkaConfigImpl(conf: Config) extends KafkaConfig {
    override val brokers: String = conf.getString("brokers")
  }
}

sealed trait ClientConfig {
  def minBackoff: FiniteDuration
  def maxBackoff: FiniteDuration
  def randomBackoffFactor: Double
}

object ClientConfig {
  private[kafka] class ClientConfigImpl(conf: Config) extends ClientConfig {
    val minBackoff = conf.getDuration("failure-exponential-backoff.min", TimeUnit.MILLISECONDS).millis
    val maxBackoff = conf.getDuration("failure-exponential-backoff.max", TimeUnit.MILLISECONDS).millis
    val randomBackoffFactor = conf.getDouble("failure-exponential-backoff.random-factor")
  }
}

sealed trait ProducerConfig extends ClientConfig {
  def role: Option[String]
}

object ProducerConfig {
  def apply(conf: Config): ProducerConfig =
    new ProducerConfigImpl(conf.getConfig("lagom.broker.kafka.client.producer"))

  private class ProducerConfigImpl(conf: Config)
    extends ClientConfig.ClientConfigImpl(conf) with ProducerConfig {
    val role = conf.getString("role") match {
      case ""    => None
      case other => Some(other)
    }
  }
}

sealed trait ConsumerConfig extends ClientConfig {
  def batchingSize: Int
  def batchingInterval: FiniteDuration
}

object ConsumerConfig {
  def apply(conf: Config): ConsumerConfig =
    new ConsumerConfigImpl(conf.getConfig("lagom.broker.kafka.client.consumer"))

  private final class ConsumerConfigImpl(conf: Config)
    extends ClientConfig.ClientConfigImpl(conf)
    with ConsumerConfig {

    override val batchingSize: Int = conf.getInt("batching-size")
    override val batchingInterval: FiniteDuration = {
      val interval = conf.getDuration("batching-interval")
      FiniteDuration(interval.toMillis(), TimeUnit.MILLISECONDS)
    }
  }
}
