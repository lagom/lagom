/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.testkit

import java.util.concurrent.CompletionStage

import javax.inject.Inject
import akka.Done
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.javadsl.api.MethodTopicHolder
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.api.broker.Message
import com.lightbend.lagom.javadsl.api.broker.Subscriber
import com.lightbend.lagom.javadsl.api.broker.Topic
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.Offset

import scala.collection.JavaConverters._

/**
 * Topic factory that connects consumers directly to the implementing producers.
 */
class TestTopicFactory @Inject() (resolvedServices: ResolvedServices, materializer: Materializer) extends TopicFactory {
  private val topics: Map[TopicId, Any] = resolvedServices.services.flatMap { service =>
    service.descriptor.topicCalls().asScala.map { topicCall =>
      topicCall.topicId -> service.service
    }
  }.toMap

  override def create[Payload](topicCall: TopicCall[Payload]): Topic[Payload] = {
    topics.get(topicCall.topicId()) match {
      case Some(service) =>
        topicCall.topicHolder() match {
          case method: MethodTopicHolder =>
            method.create(service) match {
              case topicProducer: TaggedOffsetTopicProducer[Payload, _] =>
                new TestTopic(topicCall, topicProducer)
              case other =>
                throw new IllegalArgumentException(s"Testkit does not know how to handle topic $other")
            }
        }
      case None => throw new IllegalArgumentException(s"$topicCall hasn't been resolved.")
    }
  }

  private class TestTopic[Payload, Event <: AggregateEvent[Event]](
      topicCall: TopicCall[Payload],
      topicProducer: TaggedOffsetTopicProducer[Payload, Event]
  ) extends Topic[Payload] {

    // Create a source for all the tags, and merge them all together.
    // Then, send the flow through a serializer and deserializer, to simulate sending it over the wire.
    private val messageSource: scaladsl.Source[Message[Payload], NotUsed] = {
      val serializer   = topicCall.messageSerializer().serializerForRequest()
      val deserializer = topicCall.messageSerializer().deserializer(serializer.protocol())
      Source
        .from(topicProducer.tags)
        .asScala
        .flatMapMerge(topicProducer.tags.size(), { tag =>
          topicProducer.readSideStream.apply(tag, Offset.NONE).asScala.map(_.first)
        })
        .map { message =>
          val bytes = serializer.serialize(message.payload)
          message.withPayload(bytes)
        }
        .map { messageWithBytes =>
          val payload = deserializer.deserialize(messageWithBytes.payload)
          messageWithBytes.withPayload(payload)
        }
    }

    override def topicId: TopicId = topicCall.topicId

    override def subscribe(): Subscriber[Payload] = new TestSubscriber

    private class TestSubscriber extends Subscriber[Payload] {
      override def withGroupId(groupId: String): Subscriber[Payload] = this

      override def withMetadata(): Subscriber[Message[Payload]] = new TestSubscriberWithMetadata

      override def atMostOnceSource(): Source[Payload, _] = messageSource.map(_.payload()).asJava

      override def atLeastOnce(flow: Flow[Payload, Done, _]): CompletionStage[Done] =
        atMostOnceSource().via(flow).runWith(Sink.ignore[Done], materializer)
    }

    private class TestSubscriberWithMetadata extends Subscriber[Message[Payload]] {
      override def withGroupId(groupId: String): Subscriber[Message[Payload]] = this

      override def withMetadata(): Subscriber[Message[Message[Payload]]] =
        throw new UnsupportedOperationException("Subscriber already has metadata")

      override def atMostOnceSource(): Source[Message[Payload], _] = messageSource.asJava

      override def atLeastOnce(flow: Flow[Message[Payload], Done, _]): CompletionStage[Done] =
        atMostOnceSource().via(flow).runWith(Sink.ignore[Done], materializer)
    }
  }
}
