/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFunction }
import javax.inject.{ Inject, Singleton }

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.stream.Materializer
import com.lightbend.lagom.internal.javadsl.testkit.TopicStub
import com.lightbend.lagom.internal.testkit.TopicBufferActor
import com.lightbend.lagom.javadsl.api.broker.Topic

/**
 * Factors [[com.lightbend.lagom.javadsl.testkit.ProducerStub]]'s. This is a singleton that should be [[javax.inject.Inject]]ed
 * on the stubbed Services when writing tests.
 */
@Singleton
final class ProducerStubFactory @Inject() (actorSystem: ActorSystem, materializer: Materializer) {

  private val topics = new ConcurrentHashMap[String, ProducerStub[_]]

  def producer[T](topicId: String): ProducerStub[T] = {
    val builder = new JFunction[String, ProducerStub[_]] {
      override def apply(t: String) = new ProducerStub[T](t, actorSystem, materializer)
    }
    topics.computeIfAbsent(topicId, builder).asInstanceOf[ProducerStub[T]]
  }
}

/**
 * Stubs the production end of a [[com.lightbend.lagom.javadsl.api.broker.Topic]] so that test writers can mock
 * message production from upstream services into topics consumed by services under test.
 */
final class ProducerStub[T] private[lagom] (topicName: String, actorSystem: ActorSystem, materializer: Materializer) {
  private lazy val bufferActor = {
    val bufferProps: Props = Props.create(classOf[TopicBufferActor])
    actorSystem.actorOf(bufferProps)
  }

  /**
   * Returns the [[com.lightbend.lagom.javadsl.api.broker.Topic]] where this [[com.lightbend.lagom.javadsl.testkit.ProducerStub]] is connected to.
   */
  val topic: Topic[T] = new TopicStub[T](Topic.TopicId.of(topicName), bufferActor)(materializer)

  /**
   * Sends the message via the [[com.lightbend.lagom.javadsl.api.broker.Topic]] where this [[com.lightbend.lagom.javadsl.testkit.ProducerStub]] is connected to.
   *
   * @param message
   */
  def send(message: T): Unit = bufferActor.tell(message, ActorRef.noSender)

}

