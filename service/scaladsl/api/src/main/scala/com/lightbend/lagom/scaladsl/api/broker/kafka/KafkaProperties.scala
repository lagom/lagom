/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.broker.kafka

import com.lightbend.lagom.scaladsl.api.Descriptor

/**
 * Provides a set of Kafka specific properties that can be used when creating a topic descriptor.
 */
object KafkaProperties {

  /**
   * A PartitionKeyStrategy produces a key for each message published to a Kafka topic.
   *
   * The key is used to determine on which topic's partition a message is published. It is guaranteed that messages
   * with the same key will arrive to the same partition, in the order they are published.
   */
  def partitionKeyStrategy[Message]: Descriptor.Property[Message, PartitionKeyStrategy[Message]] =
    Descriptor.Property[Message, PartitionKeyStrategy[Message]]("kafkaPartitionKeyStrategy")
}
