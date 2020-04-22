/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api.broker.kafka

/**
 * Defines an algorithm for producing a key from a Message.
 */
trait PartitionKeyStrategy[-Payload] {

  /**
   * Computes a key from a message payload.
   * The key is used to decide on what topic's partition a message should be published to.
   *
   * @param message The message to publish into a Kafka topic.
   * @return A key used to decide on what topic's partition the passed message is published to.
   */
  def computePartitionKey(message: Payload): String
}

object PartitionKeyStrategy {

  /**
   * Create a partition key strategy.
   */
  def apply[Payload](f: Payload => String): PartitionKeyStrategy[Payload] = new PartitionKeyStrategy[Payload] {
    override def computePartitionKey(message: Payload): String = f(message)
  }
}
