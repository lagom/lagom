/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.broker.kafka;

/** Defines an algorithm for producing a key from a Message payload. */
@FunctionalInterface
public interface PartitionKeyStrategy<Payload> {

  /**
   * Computes a key from a message payload. The key is used to decide on what topic's partition a
   * message should be published to.
   *
   * @param payload The message payload to publish into a Kafka topic.
   * @return A key used to decide on what topic's partition the passed message is published to.
   */
  String computePartitionKey(Payload payload);
}
