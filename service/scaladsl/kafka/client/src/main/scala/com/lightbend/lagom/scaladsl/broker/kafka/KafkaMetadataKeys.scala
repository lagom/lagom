/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.broker.kafka

import com.lightbend.lagom.scaladsl.api.broker.MetadataKey
import org.apache.kafka.common.header.Headers

/**
 * Metadata keys specific to the Kafka broker implementation.
 */
object KafkaMetadataKeys {

  /**
   * The partition the message is published to.
   */
  val Partition: MetadataKey[Int] = MetadataKey("kafkaPartition")

  /**
   * The offset of the message in its partition.
   */
  val Offset: MetadataKey[Long] = MetadataKey("kafkaOffset")

  /**
   * The topic the message is published to.
   */
  val Topic: MetadataKey[String] = MetadataKey("kafkaTopic")

  /**
   * The Kafka message headers.
   */
  val Headers: MetadataKey[Headers] = MetadataKey("kafkaHeaders")
}
