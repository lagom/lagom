/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.broker.kafka;

import com.lightbend.lagom.javadsl.api.broker.MetadataKey;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.record.TimestampType;

/** Metadata keys specific to the Kafka broker implementation. */
public final class KafkaMetadataKeys {

  private KafkaMetadataKeys() {}

  /** The partition the message is published to. */
  public static final MetadataKey<Integer> PARTITION = MetadataKey.named("kafkaPartition");

  /** The offset of the message in its partition. */
  public static final MetadataKey<Long> OFFSET = MetadataKey.named("kafkaOffset");

  /** The topic the message is published to. */
  public static final MetadataKey<String> TOPIC = MetadataKey.named("kafkaTopic");

  /** The Kafka message headers. */
  public static final MetadataKey<Headers> HEADERS = MetadataKey.named("kafkaHeaders");

  /**
   * The timestamp of the Kafka message. This could have a different meaning depending on
   * TimestampType.
   */
  public static final MetadataKey<Long> TIMESTAMP = MetadataKey.named("kafkaTimestamp");

  /** The timestamp type of the Kafka message. */
  public static final MetadataKey<TimestampType> TIMESTAMP_TYPE =
      MetadataKey.named("kafkaTimestampType");
}
