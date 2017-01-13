/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }

import akka.Done
import akka.actor.{ Actor, ActorRef, Props }
import akka.stream.javadsl.{ Flow => JFlow, Keep => JKeep, Sink => JSink, Source => JSource }
import akka.stream.scaladsl.Source
import akka.stream.{ Materializer, OverflowStrategy }
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.api.broker.{ Subscriber, Topic }

import scala.collection.mutable

private[lagom] class TopicStub[T](topicName: String, topicBuffer: ActorRef, materializer: Materializer) extends Topic[T] {
  def topicId(): Topic.TopicId = TopicId.of(topicName)
  def subscribe = new SubscriberStub[T](topicName)(topicBuffer, materializer)
}

private[lagom] object TopicBufferActor {
  def props(): Props = Props(new TopicBufferActor())
  case class SubscribeToBuffer(groupId: String, actorRef: ActorRef)
}

private[lagom] class TopicBufferActor extends Actor {

  import TopicBufferActor._

  var downstreams = Map.empty[String, ActorRef]
  val buffer: mutable.Buffer[AnyRef] = mutable.Buffer.empty[AnyRef]

  override def receive: Receive = {
    case SubscribeToBuffer(groupId, ref) => {
      downstreams = downstreams + (groupId -> ref)
      buffer.foreach(msg => ref.tell(msg, ActorRef.noSender))
    }
    case message: AnyRef => {
      downstreams.values.foreach(ref => ref ! message)
      buffer append message
    }
  }
}

private[lagom] class SubscriberStub[T](topicId: String, groupId: String = "default")(topicBuffer: ActorRef, materializer: Materializer) extends Subscriber[T] {

  def withGroupId(groupId: String): Subscriber[T] = new SubscriberStub[T](topicId, groupId)(topicBuffer, materializer)

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

  private def subscribeToBuffer[T](ref: ActorRef, t: T) = {
    topicBuffer.tell(TopicBufferActor.SubscribeToBuffer(groupId, ref), ActorRef.noSender)
    t
  }
}
