/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.api.broker

import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.broker.Topic

/**
 * Internal API
 *
 * Abstraction for topic factories.
 */
trait TopicFactory {
  /**
   * Create a client topic for the given topic call.
   */
  def create[Message](topicCall: TopicCall[Message]): Topic[Message]
}

/**
 * Provider with a default topic factory.
 *
 * Anything that wants to optionally depend on a topic factory should extend this, which allows the actual topic
 * factory to override it and guarantee that its implementation will be used and not this default one.
 */
trait TopicFactoryProvider {
  def optionalTopicFactory: Option[TopicFactory] = None
}
