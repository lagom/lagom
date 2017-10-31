/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFunction }

import akka.Done
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Source }
import com.lightbend.lagom.internal.testkit.{ InternalSubscriberStub, TopicBufferActor }
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.{ Message, Subscriber, Topic }

import scala.concurrent.Future

/**
 * Factors [[com.lightbend.lagom.scaladsl.testkit.ProducerStub]]'s.
 */
final class ProducerStubFactory(actorSystem: ActorSystem, materializer: Materializer) {

  private val topics = new ConcurrentHashMap[String, ProducerStub[_]]

  def producer[T](topicId: String): ProducerStub[T] = {
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

  val topic: Topic[T] = new TopicStub[T](TopicId(topicName), bufferActor)(materializer)

  /**
   * Send a message payload to the topic.
   *
   * @param message The message to send.
   */
  def send(message: T): Unit = bufferActor.tell(Message(message), ActorRef.noSender)

  /**
   * Send a message wrapped with metadata to the topic.
   *
   * @param message The message to send.
   */
  def send(message: Message[T]): Unit = bufferActor.tell(message, ActorRef.noSender)
}

private[lagom] class TopicStub[T](val topicId: Topic.TopicId, topicBuffer: ActorRef)(implicit materializer: Materializer) extends Topic[T] {
  def subscribe = new SubscriberStub[T, T]("default", topicBuffer, _.payload)

  class SubscriberStub[Payload, SubscriberPayload](groupId: String, topicBuffer: ActorRef, transform: Message[Payload] => SubscriberPayload)(implicit materializer: Materializer)
    extends InternalSubscriberStub[Payload, Message](groupId, topicBuffer) with Subscriber[SubscriberPayload] {

    override def withMetadata: Subscriber[Message[SubscriberPayload]] =
      new SubscriberStub[Payload, Message[SubscriberPayload]](groupId, topicBuffer,
        msg => msg.withPayload(transform(msg)))

    override def withGroupId(groupId: String): Subscriber[SubscriberPayload] =
      new SubscriberStub[Payload, SubscriberPayload](groupId, topicBuffer, transform)

    override def atMostOnceSource: Source[SubscriberPayload, _] = super.mostOnceSource.map(transform)

    override def atLeastOnce(flow: Flow[SubscriberPayload, Done, _]): Future[Done] =
      super.leastOnce(Flow[Message[Payload]].map(transform).via(flow))
  }

}
