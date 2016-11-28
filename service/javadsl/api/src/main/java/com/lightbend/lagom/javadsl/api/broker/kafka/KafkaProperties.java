/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker.kafka;

import com.lightbend.lagom.javadsl.api.Descriptor;

/**
 * Provides a set of Kafka specific properties that can be used when creating a topic descriptor.
 */
public final class KafkaProperties {
  private KafkaProperties() {}

  private static final Descriptor.Properties.Property PARTITION_KEY_STRATEGY =
          new Descriptor.Properties.Property<>(PartitionKeyStrategy.class, "kafkaPartitionKeyStrategy");

  /**
   * A PartitionKeyStrategy produces a key for each message published to a Kafka topic.
   *
   * The key is used to determine on which topic's partition a message is published. It is guaranteed that messages
   * with the same key will arrive to the same partition, in the order they are published.
   */
  @SuppressWarnings("unchecked")
  public static <Message> Descriptor.Properties.Property<Message, PartitionKeyStrategy<Message>> partitionKeyStrategy() {
    return PARTITION_KEY_STRATEGY;
  }
}
