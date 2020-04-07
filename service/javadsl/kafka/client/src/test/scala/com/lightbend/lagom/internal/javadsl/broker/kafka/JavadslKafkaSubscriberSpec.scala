/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.broker.kafka

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 *
 */
class JavadslKafkaSubscriberSpec extends AnyFlatSpec with Matchers {
  behavior.of("JavadslKafkaSubscriber")

  it should "create a new subscriber with updated groupId" in {
    val subscriber =
      new JavadslKafkaSubscriber(null, null, JavadslKafkaSubscriber.GroupId("old"), null, null, null, null)(null, null)
    subscriber.withGroupId("newGID") should not be subscriber
  }
}
