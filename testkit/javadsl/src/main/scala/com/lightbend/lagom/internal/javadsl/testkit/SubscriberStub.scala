/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.testkit

import java.util.concurrent.CompletionStage

import akka.Done
import akka.actor.ActorRef
import akka.stream.javadsl.{ Flow => JFlow, Keep => JKeep, Sink => JSink, Source => JSource }
import akka.stream.scaladsl.Source
import akka.stream.{ Materializer, OverflowStrategy }
import com.lightbend.lagom.internal.testkit.TopicBufferActor
import com.lightbend.lagom.javadsl.api.broker.Subscriber

private[lagom] class SubscriberStub[T](groupId: String, topicBuffer: ActorRef)(implicit materializer: Materializer) extends Subscriber[T] {

  def withGroupId(groupId: String): Subscriber[T] = new SubscriberStub[T](groupId, topicBuffer)

  def atMostOnceSource: JSource[T, _] = {
    Source
      .actorRef[T](1024, OverflowStrategy.fail)
      .prependMat(Source.empty)(subscribeToBuffer)
      .asJava
      .asInstanceOf[JSource[T, _]]
  }

  def atLeastOnce(flow: JFlow[T, Done, _]): CompletionStage[Done] = {
    atMostOnceSource
      .via(flow)
      .toMat(JSink.ignore, JKeep.right[Any, CompletionStage[Done]]).run(materializer)
  }

  private def subscribeToBuffer[R](ref: ActorRef, t: R) = {
    topicBuffer.tell(TopicBufferActor.SubscribeToBuffer(groupId, ref), ActorRef.noSender)
    t
  }
}
