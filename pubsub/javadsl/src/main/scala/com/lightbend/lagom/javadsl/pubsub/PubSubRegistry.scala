/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.pubsub

/**
 * Publish and subscribe to a topic is performed with a [[PubSubRef]] and
 * that is retrieved via this registry.
 */
trait PubSubRegistry {
  /**
   * Get a [[PubSubRef]] for a given topic.
   */
  def refFor[T](topic: TopicId[T]): PubSubRef[T]
}
