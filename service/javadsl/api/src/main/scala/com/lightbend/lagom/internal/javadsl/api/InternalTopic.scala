/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.api

import com.lightbend.lagom.javadsl.api.broker.Subscriber
import com.lightbend.lagom.javadsl.api.broker.Topic

trait InternalTopic[Message] extends Topic[Message] {
  final override def topicId(): Topic.TopicId = throw new UnsupportedOperationException("Topic#topicId is not permitted in the service's topic implementation")

  final override def subscribe(): Subscriber[Message] =
    throw new UnsupportedOperationException("Topic#subscribe is not permitted in the service's topic implementation.")
}
