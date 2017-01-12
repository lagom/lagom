/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import java.util.concurrent.{ CompletionStage, ConcurrentHashMap }
import javax.inject.{ Inject, Singleton }

import akka.Done
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.javadsl.{ Flow => JFlow, Keep => JKeep, Sink => JSink, Source => JSource }
import akka.stream.scaladsl.Source
import com.lightbend.lagom.javadsl.api.broker.{ Subscriber, Topic }
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId

import scala.collection.mutable
import java.util.function.{ Function => JFunction }

@Singleton
final class TopicStubFactory @Inject() (actorSystem: ActorSystem, materializer: Materializer) {

  private val topics = new ConcurrentHashMap[String, TopicStub[_]]

  def forId[T](clzz: Class[T], topicId: String): TopicStub[T] = {
    val builder = new JFunction[String, TopicStub[_]] {
      override def apply(t: String): TopicStub[_] = {
        new TopicStub[T](t, actorSystem, materializer)
      }
    }
    topics.computeIfAbsent(topicId, builder).asInstanceOf[TopicStub[T]]
  }
}

trait ProducerStub[T] {
  /**
   * Sends the message via the {@link TopicStub} where this {@link ProducerStub} is connected to.
   *
   * @param message
   */
  def send(message: T): Unit
}

final class TopicStub[T](topicName: String, actorSystem: ActorSystem, materializer: Materializer) extends Topic[T] {
  private val bufferActor = {
    val bufferProps: Props = Props.create(classOf[TopicBufferActor])
    actorSystem.actorOf(bufferProps)
  }

  def topicId(): Topic.TopicId = TopicId.of(topicName)

  def subscribe = new SubscriberStub[T](topicName)(bufferActor, materializer)

  /**
   * @return a { @link ProducerStub} that clients (test code) can use to send messages through this { @link TopicStub}.
   */
  def producerStub: ProducerStub[T] = new ProducerStub[T] {
    override def send(message: T): Unit = bufferActor.tell(message, ActorRef.noSender)
  }
}

private[lagom] object TopicBufferActor {
  def props(): Props = Props(new TopicBufferActor())

  case class SubscribeToBuffer(groupId: String, actorRef: ActorRef)

}

private[lagom] class TopicBufferActor extends Actor {

  import TopicBufferActor._

  //  private val buffer = new util.ArrayList[AnyRef]
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

  @throws[IllegalArgumentException]
  def withGroupId(groupId: String): Subscriber[T] = new SubscriberStub[T](topicId, groupId)(topicBuffer, materializer)

  def atMostOnceSource: JSource[T, _] = {
    Source
      .actorRef[T](1024, OverflowStrategy.fail)
      .prependMat(Source.empty)(subscribeToBuffer)
      .asJava
      .asInstanceOf[JSource[T, _]]
  }

  private def subscribeToBuffer[T](ref: ActorRef, t: T) = {
    topicBuffer.tell(TopicBufferActor.SubscribeToBuffer(groupId, ref), ActorRef.noSender)
    t
  }

  def atLeastOnce(flow: JFlow[T, Done, _]): CompletionStage[Done] = {
    atMostOnceSource
      .via(flow)
      .toMat(JSink.ignore, JKeep.right[Any, CompletionStage[Done]]).run(materializer)
  }

}
