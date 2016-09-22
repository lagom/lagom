/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.broker

import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.broker.Topic

/**
 * Factory for creating topics.
 *
 * Note: This class is useful only to create new message broker module implementations,
 * and should not leak into the user api.
 */
trait TopicFactory {
  def create[Message](topicCall: TopicCall[Message]): Topic[Message]
}
