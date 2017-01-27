/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
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

  /**
   * The name of the topic publisher.
   *
   * Since topic publishers don't actually provide any components, they just consume a LagomServer and publish the
   * topics they find there, this can be used to signal that a topic publisher has been provided to publish
   * topics, so that the LagomServerComponents can detect a misconfiguration where one hasn't been provided.
   *
   * @return The name of the topic publisher that has published topics, if one has been provided.
   */
  def topicPublisherName: Option[String] = None
}
