/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.pubsub

object TopicId {
  def of[T](messageClass: Class[T], qualifier: String): TopicId[T] =
    new TopicId(messageClass, qualifier)
}

/**
 * Identifier of a topic. The messages of this topic will be instances of the
 * `messageClass` or subclasses thereof. The `qualifier` can be used to
 * distinguish topics that are using the same `messageClass`. In other words
 * the identifier of the topic is the combination of the `messageClass` and
 * the `qualifier`.
 *
 * @tparam T the type of the messages that can be published to this topic
 */
final case class TopicId[T](messageClass: Class[T], qualifier: String) {
  val name: String = {
    if (qualifier == null || qualifier == "") messageClass.getName
    else messageClass.getName + "-" + qualifier
  }
}
