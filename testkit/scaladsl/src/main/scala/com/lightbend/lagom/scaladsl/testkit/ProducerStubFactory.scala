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
import com.lightbend.lagom.scaladsl.api.broker.{ Subscriber, Topic }

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
  def send(message: T): Unit = bufferActor.tell(message, ActorRef.noSender)
}

private[lagom] class TopicStub[T](val topicId: Topic.TopicId, topicBuffer: ActorRef)(implicit materializer: Materializer) extends Topic[T] {
  def subscribe = new SubscriberStub[T]("default", topicBuffer)

  class SubscriberStub[Message](groupId: String, topicBuffer: ActorRef)(implicit materializer: Materializer)
    extends InternalSubscriberStub(groupId, topicBuffer) with Subscriber[Message] {

    override def withGroupId(groupId: String): Subscriber[Message] = new SubscriberStub[Message](groupId, topicBuffer)
    override def atMostOnceSource: Source[Message, _] = super.mostOnceSource
    override def atLeastOnce(flow: Flow[Message, Done, _]): Future[Done] = super.leastOnce(flow)
  }

}
