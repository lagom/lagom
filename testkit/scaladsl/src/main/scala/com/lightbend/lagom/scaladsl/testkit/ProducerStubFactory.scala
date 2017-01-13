/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFunction }

import akka.Done
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy }
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.{ Subscriber, Topic }

import scala.collection.mutable
import scala.concurrent.Future

/**
 * Factors [[com.lightbend.lagom.scaladsl.testkit.ProducerStub]]'s.
 */
final class ProducerStubFactory(actorSystem: ActorSystem, materializer: Materializer) {

  private val topics = new ConcurrentHashMap[String, ProducerStub[_]]

  def producer[T](clzz: Class[T], topicId: String): ProducerStub[T] = {
    val builder = new JFunction[String, ProducerStub[_]] {
      override def apply(t: String) = new ProducerStub[T](t, actorSystem, materializer)
    }
    topics.computeIfAbsent(topicId, builder).asInstanceOf[ProducerStub[T]]
  }

}

/**
 * Stubs the production end of a [[com.lightbend.lagom.scaladsl.api.broker.Topic]] so that test writers can mock
 * message production from upstream services into topics consumed by services under test.
 */
final class ProducerStub[T] private[lagom] (topicName: String, actorSystem: ActorSystem, materializer: Materializer) {
  private lazy val bufferActor = {
    val bufferProps: Props = Props.create(classOf[TopicBufferActor])
    actorSystem.actorOf(bufferProps)
  }

  private lazy val _topic = new TopicStub[T](topicName, bufferActor)(materializer)

  def send(message: T): Unit = bufferActor.tell(message, ActorRef.noSender)

  def topic(): Topic[T] = _topic
}

private[lagom] class TopicStub[T](topicName: String, topicBuffer: ActorRef)(implicit materializer: Materializer) extends Topic[T] {
  def topicId(): Topic.TopicId = TopicId(topicName)

  def subscribe = new SubscriberStub[T](topicName, "default", topicBuffer)
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

private[lagom] class SubscriberStub[Message](
  topicId:     String,
  groupId:     String,
  topicBuffer: ActorRef
)(implicit materializer: Materializer)
  extends Subscriber[Message] {

  def withGroupId(groupId: String): Subscriber[Message] = new SubscriberStub[Message](topicId, groupId, topicBuffer)

  def atMostOnceSource: Source[Message, _] = {
    Source
      .actorRef[Message](1024, OverflowStrategy.fail)
      .prependMat(Source.empty)(subscribeToBuffer)
  }

  def atLeastOnce(flow: Flow[Message, Done, _]): Future[Done] = {
    atMostOnceSource
      .via(flow)
      .toMat(Sink.ignore)(Keep.right[Any, Future[Done]])
      .run()
  }

  private def subscribeToBuffer[R](ref: ActorRef, t: R) = {
    topicBuffer.tell(TopicBufferActor.SubscribeToBuffer(groupId, ref), ActorRef.noSender)
    t
  }
}