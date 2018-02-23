/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.testkit

import java.util.concurrent.CompletionStage

import akka.Done
import akka.actor.ActorRef
import akka.stream.Materializer
import akka.stream.javadsl.{ Flow, Source }
import akka.stream.scaladsl.{ Flow => ScalaFlow }
import com.lightbend.lagom.internal.testkit.InternalSubscriberStub
import com.lightbend.lagom.javadsl.api.broker.{ Message, Subscriber, Topic }

import scala.compat.java8.FutureConverters.toJava

private[lagom] class TopicStub[T](val topicId: Topic.TopicId, topicBuffer: ActorRef)(implicit materializer: Materializer) extends Topic[T] {

  // TODO: use ServiceInfo's name as a default value.
  def subscribe = new SubscriberStub("default", topicBuffer, _.getPayload)

  class SubscriberStub[SubscriberPayload](groupId: String, topicBuffer: ActorRef, transform: Message[T] => SubscriberPayload)(implicit materializer: Materializer)
    extends InternalSubscriberStub[T, Message](groupId, topicBuffer)(materializer) with Subscriber[SubscriberPayload] {

    override def withGroupId(groupId: String): Subscriber[SubscriberPayload] =
      new SubscriberStub(groupId, topicBuffer, transform)

    override def withMetadata(): Subscriber[Message[SubscriberPayload]] =
      new SubscriberStub[Message[SubscriberPayload]](groupId, topicBuffer, msg => msg.withPayload(transform(msg)))

    override def atMostOnceSource(): Source[SubscriberPayload, _] =
      super.mostOnceSource.map(transform).asJava

    override def atLeastOnce(flow: Flow[SubscriberPayload, Done, _]): CompletionStage[Done] =
      toJava(super.leastOnce(ScalaFlow[Message[T]].map(transform).via(flow.asScala)))
  }
}
