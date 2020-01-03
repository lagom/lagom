/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.broker.kafka

import org.scalatest.FlatSpec
import org.scalatest.Matchers

/**
 *
 */
class JavadslKafkaSubscriberSpec extends FlatSpec with Matchers {
  behavior.of("JavadslKafkaSubscriber")

  it should "create a new subscriber with updated groupId" in {
    val subscriber =
      new JavadslKafkaSubscriber(null, null, JavadslKafkaSubscriber.GroupId("old"), null, null, null, null)(null, null)
    subscriber.withGroupId("newGID") should not be subscriber
  }
}
