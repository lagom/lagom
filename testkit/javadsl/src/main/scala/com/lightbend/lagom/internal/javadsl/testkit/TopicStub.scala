/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.testkit

import akka.actor.ActorRef
import akka.stream.Materializer
import com.lightbend.lagom.javadsl.api.broker.Topic

private[lagom] class TopicStub[T](val topicId: Topic.TopicId, topicBuffer: ActorRef)(implicit materializer: Materializer) extends Topic[T] {

  def subscribe = new SubscriberStub[T]("default", topicBuffer)
}
