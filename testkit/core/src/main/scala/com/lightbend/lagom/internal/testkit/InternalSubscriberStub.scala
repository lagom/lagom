/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import akka.Done
import akka.actor.ActorRef
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }

import scala.concurrent.Future

private[lagom] class InternalSubscriberStub[Message](
  groupId:     String,
  topicBuffer: ActorRef
)(implicit materializer: Materializer) {

  def mostOnceSource: Source[Message, _] = {
    Source
      .actorRef[Message](1024, OverflowStrategy.fail)
      .prependMat(Source.empty)(subscribeToBuffer)
  }

  def leastOnce(flow: Flow[Message, Done, _]): Future[Done] = {
    mostOnceSource
      .via(flow)
      .toMat(Sink.ignore)(Keep.right[Any, Future[Done]])
      .run()
  }

  private def subscribeToBuffer[R](ref: ActorRef, t: R) = {
    topicBuffer.tell(TopicBufferActor.SubscribeToBuffer(groupId, ref), ActorRef.noSender)
    t
  }
}
