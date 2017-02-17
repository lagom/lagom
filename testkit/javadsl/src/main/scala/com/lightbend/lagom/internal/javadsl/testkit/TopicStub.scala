/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.testkit

import java.util.concurrent.CompletionStage

import akka.Done
import akka.actor.ActorRef
import akka.stream.Materializer
import akka.stream.javadsl.{ Flow, Source }
import com.lightbend.lagom.internal.testkit.InternalSubscriberStub
import com.lightbend.lagom.javadsl.api.broker.{ Subscriber, Topic }

import scala.compat.java8.FutureConverters.toJava

private[lagom] class TopicStub[T](val topicId: Topic.TopicId, topicBuffer: ActorRef)(implicit materializer: Materializer) extends Topic[T] {

  // TODO: use ServiceInfo's name as a default value.
  def subscribe = new SubscriberStub("default", topicBuffer)

  class SubscriberStub(groupId: String, topicBuffer: ActorRef)(implicit materializer: Materializer)
    extends InternalSubscriberStub[T](groupId, topicBuffer)(materializer) with Subscriber[T] {

    override def withGroupId(groupId: String): Subscriber[T] = new SubscriberStub(groupId, topicBuffer)
    override def atMostOnceSource(): Source[T, _] = super.mostOnceSource.asJava
    override def atLeastOnce(flow: Flow[T, Done, _]): CompletionStage[Done] = toJava(super.leastOnce(flow.asScala))
  }
}
