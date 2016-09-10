/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.broker.kafka;

import com.lightbend.lagom.javadsl.api.broker.modules.Properties.Property;
import com.lightbend.lagom.javadsl.broker.kafka.property.PartitionKeyStrategy;

/**
 * Provides a set of properties that can be used when creating a topic descriptor.
 */
public final class Properties {
  private Properties() {}
  /**
   * A PartitionKeyStrategy produces a key for each message published to a Kafka topic.
   * The key is used to determine on which topic's partition a message is published.
   * It is guaranteed that messages with the same key will arrive to the same partition.   
   */
  public static Property<PartitionKeyStrategy<?>> PARTITION_KEY_STRATEGY = new Property<PartitionKeyStrategy<?>>();
}
