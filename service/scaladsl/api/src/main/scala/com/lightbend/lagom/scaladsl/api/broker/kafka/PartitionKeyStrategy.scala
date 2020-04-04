/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api.broker.kafka

import com.lightbend.lagom.internal.api.broker.MessageWithMetadata

/**
 * Defines an algorithm for producing a key from a Message.
 */
trait PartitionKeyStrategy[Payload] {

  /**
   * Computes a key from a message. The key is used to decide on what topic's partition a message should be published
   * to.
   *
   * @param message The message payload to publish into a Kafka topic.
   * @return A key used to decide on what topic's partition the passed message is published to.
   */
  def computePartitionKey(message: MessageWithMetadata[Payload]): String
}

object PartitionKeyStrategy {

  /**
   * Create a partition key strategy from payload.
   */
  def apply[Payload](f: Payload => String): PartitionKeyStrategy[Payload] =
    (message: MessageWithMetadata[Payload]) => f(message.payload)

  /**
   * Create a partition key strategy from message with metadata.
   */
  def withMetadata[Payload](f: MessageWithMetadata[Payload] => String): PartitionKeyStrategy[Payload] =
    (message: MessageWithMetadata[Payload]) => f(message)
}
