/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.pubsub

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
