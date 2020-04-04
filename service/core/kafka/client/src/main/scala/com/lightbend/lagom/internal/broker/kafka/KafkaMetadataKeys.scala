/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker.kafka

import com.lightbend.lagom.internal.api.broker.MessageMetadataKey
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.record.TimestampType

/**
 * Metadata keys specific to the Kafka broker implementation.
 */
object KafkaMetadataKeys {

  /**
   * The partition the message is published to.
   */
  val Partition: MessageMetadataKey[Int] = MessageMetadataKey("kafkaPartition")

  /**
   * The offset of the message in its partition.
   */
  val Offset: MessageMetadataKey[Long] = MessageMetadataKey("kafkaOffset")

  /**
   * The topic the message is published to.
   */
  val Topic: MessageMetadataKey[String] = MessageMetadataKey("kafkaTopic")

  /**
   * The Kafka message headers.
   */
  val Headers: MessageMetadataKey[Headers] = MessageMetadataKey("kafkaHeaders")

  /**
   * The timestamp of the Kafka message. This could have a different meaning depending on TimestampType.
   */
  val Timestamp: MessageMetadataKey[Long] = MessageMetadataKey("kafkaTimestamp")

  /**
   * The timestamp type of the Kafka message.
   */
  val TimestampType: MessageMetadataKey[TimestampType] = MessageMetadataKey("kafkaTimestampType")

}
