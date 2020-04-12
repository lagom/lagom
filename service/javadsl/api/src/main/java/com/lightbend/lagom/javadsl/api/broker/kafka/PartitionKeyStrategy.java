/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.broker.kafka;

import com.lightbend.lagom.internal.api.broker.MessageWithMetadata;
import com.lightbend.lagom.javadsl.api.broker.Message;

/** Defines an algorithm for producing a key from a Message. */
@FunctionalInterface
public interface PartitionKeyStrategy<Payload> {

  /**
   * Computes a key from a message. The key is used to decide on what topic's partition a message
   * should be published to.
   *
   * @param message The message to publish into a Kafka topic.
   * @return A key used to decide on what topic's partition the passed message is published to.
   */
  String computePartitionKey(MessageWithMetadata<Payload> message);

  /**
   * Computes a key from a message payload. The key is used to decide on what topic's partition a
   * message should be published to.
   *
   * @param payload The message payload to publish into a Kafka topic.
   * @return A key used to decide on what topic's partition the passed message is published to.
   */
  default String computePartitionKey(Payload payload) {
    return computePartitionKey(Message.create(payload));
  }
}
