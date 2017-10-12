/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.broker.kafka;

import com.lightbend.lagom.javadsl.api.broker.MetadataKey;
import org.apache.kafka.common.header.Headers;

/**
 * Metadata keys specific to the Kafka broker implementation.
 */
public final class KafkaMetadataKeys {

  private KafkaMetadataKeys() {
  }

  /**
   * The partition the message is published to.
   */
  public static final MetadataKey<Integer> PARTITION = MetadataKey.named("kafkaPartition");

  /**
   * The offset of the message in its partition.
   */
  public static final MetadataKey<Long> OFFSET = MetadataKey.named("kafkaOffset");

  /**
   * The topic the message is published to.
   */
  public static final MetadataKey<String> TOPIC = MetadataKey.named("kafkaTopic");

  /**
   * The Kafka message headers.
   */
  public static final MetadataKey<Headers> HEADERS = MetadataKey.named("kafkaHeaders");
}
