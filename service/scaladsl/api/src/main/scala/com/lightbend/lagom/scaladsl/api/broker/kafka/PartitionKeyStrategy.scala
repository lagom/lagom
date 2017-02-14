/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.broker.kafka

/**
 * Defines an algorithm for producing a key from a Message.
 */
trait PartitionKeyStrategy[-Message] {

  /**
   * Computes a key from a message. The key is used to decide on what topic's partition a message should be published
   * to.
   *
   * @param message The message to publish into a Kafka topic.
   * @return A key used to decide on what topic's partition the passed message is published to.
   */
  def computePartitionKey(message: Message): String
}

object PartitionKeyStrategy {

  /**
   * Create a partition key strategy.
   */
  def apply[Message](f: Message => String): PartitionKeyStrategy[Message] = new PartitionKeyStrategy[Message] {
    override def computePartitionKey(message: Message): String = f(message)
  }
}
