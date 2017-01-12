/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.pubsub

import java.io.NotSerializableException
import java.util.concurrent.CompletionStage
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.NoSerializationVerificationNeeded
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import akka.stream.scaladsl
import akka.util.Timeout
import akka.pattern.ask
import akka.japi.function.Procedure
import akka.NotUsed

/**
 * A `PubSubRef` represents a publish/subscribe topic.
 *
 * Messages can be published to the topic via a stream by using the `Source`
 * returned by the [[#publisher]] method. A single message can be published
 * with the [[#publish]] method.
 *
 * Messages can be consumed from the topic via a stream by using the `Sink`
 * returned by the [[#subscriber]] method.
 *
 * The registry of subscribers is eventually consistent, i.e. new subscribers
 * are not immediately visible at other nodes, but typically the information
 * will be fully replicated to all other nodes after a few seconds.
 *
 * New subscribers will not see old messages that were published to the topic,
 * i.e. it is only a live stream of messages.
 *
 * Messages are not guaranteed to be delivered, i.e. they may be lost. That
 * can for example happen if there is a transient network partition.
 *
 * The subscriber has a buffer of received messages, but messages will be dropped
 * if that buffer is full and demand for more elements have not been requested
 * from downstream. This can happen if a subscriber is slower than the publishers
 * of the messages. When the buffer is full the oldest messages are dropped to make
 * room for new messages.
 */
final class PubSubRef[T](val topic: TopicId[T], mediator: ActorRef, system: ActorSystem,
                         bufferSize: Int)
  extends NoSerializationVerificationNeeded {
  import akka.cluster.pubsub.DistributedPubSubMediator._

  private val hasAnySubscribersTimeout = Timeout(5.seconds)

  private val publishFun: Any => Unit = {
    message => mediator ! Publish(topic.name, message)
  }

  /*
   * Publish one single message to the topic.
   */
  def publish(message: T): Unit = {
    mediator ! Publish(topic.name, message)
  }

  /**
   * Publish messages from a stream to the topic.
   * You have to connect a `Source` that produces the messages to
   * this `Sink` and then `run` the stream.
   */
  def publisher(): Sink[T, NotUsed] = {
    scaladsl.Sink.foreach[T](publishFun)
      .mapMaterializedValue(_ => NotUsed).asJava
  }

  /**
   * Consume messages from the topic via a stream.
   * You can return this `Source` as a response in a `ServiceCall`
   * and the elements will be streamed to the client.
   * Otherwise you have to connect a `Sink` that consumes the messages from
   * this `Source` and then `run` the stream.
   */
  def subscriber(): Source[T, NotUsed] = {
    scaladsl.Source.actorRef[T](bufferSize, OverflowStrategy.dropHead)
      .mapMaterializedValue { ref =>
        mediator ! Subscribe(topic.name, ref)
        NotUsed
      }.asJava
  }

  /**
   * Request if this topic has any known subscribers at this point.
   * The `CompletionStage` is completed with the currently known information
   * at this node, i.e. completion is not deferred until there are subscribers.
   *
   * Note that the registry of subscribers is eventually consistent, i.e. new
   * subscribers are not immediately visible at other nodes, but typically the
   * information will be fully replicated to all other nodes after a few seconds.
   *
   * This method is especially useful when writing tests that require that a subscriber
   * is known before sending messages to a topic.
   */
  def hasAnySubscribers(): CompletionStage[java.lang.Boolean] = {
    import scala.compat.java8.FutureConverters._
    import system.dispatcher
    implicit val timeout = hasAnySubscribersTimeout
    val result = (mediator ? GetTopics).map {
      case CurrentTopics(topics) => topics.contains(topic.name)
    }.mapTo[java.lang.Boolean]
    result.toJava
  }

  @throws(classOf[java.io.ObjectStreamException])
  protected def writeReplace(): AnyRef =
    throw new NotSerializableException(s"${getClass.getName} is not serializable. Send the entityId instead.")

  override def toString(): String = s"PubSubRef($topic)"

}
