/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.broker

/**
 * A topic can be used to publish/subscribe messages to/from a message broker.
 */
trait Topic[Message] {
  /**
   * The topic identifier.
   *
   * @return The topic identifier.
   */
  def topicId: Topic.TopicId

  /**
   * Obtain a subscriber to this topic.
   *
   * @return A Subscriber to this topic.
   */
  def subscribe: Subscriber[Message]

}

object Topic {

  /**
   * A topic identifier.
   */
  sealed trait TopicId {
    /**
     * The name of this topic
     */
    val name: String
  }

  object TopicId {
    def apply(name: String): TopicId = TopicIdImpl(name)
  }

  private case class TopicIdImpl(name: String) extends TopicId

}
