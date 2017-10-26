/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import java.util.concurrent.CompletionStage
import javax.inject.Inject

import akka.Done
import akka.stream.Materializer
import akka.stream.javadsl.{ Flow, Sink, Source }
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.javadsl.api.MethodTopicHolder
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.api.broker.{ Message, Subscriber, Topic }
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, Offset }

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

  override def create[Message](topicCall: TopicCall[Message]): Topic[Message] = {
    topics.get(topicCall.topicId()) match {
      case Some(service) =>
        topicCall.topicHolder() match {
          case method: MethodTopicHolder =>
            method.create(service) match {
              case topicProducer: TaggedOffsetTopicProducer[Message, _] =>
                new TestTopic(topicCall, topicProducer)
              case other =>
                throw new IllegalArgumentException(s"Testkit does not know how to handle topic $other")
            }
        }
      case None => throw new IllegalArgumentException(s"$topicCall hasn't been resolved.")
    }
  }

  private class TestTopic[Payload, Event <: AggregateEvent[Event]](
    topicCall:     TopicCall[Payload],
    topicProducer: TaggedOffsetTopicProducer[Payload, Event]
  ) extends Topic[Payload] {

    override def topicId = topicCall.topicId

    override def subscribe(): Subscriber[Payload] = new TestSubscriber[Payload](identity)

    private class TestSubscriber[SubscriberPayload](transform: Payload => SubscriberPayload) extends Subscriber[SubscriberPayload] {
      override def withGroupId(groupId: String): Subscriber[SubscriberPayload] = this

      override def withMetadata(): Subscriber[Message[SubscriberPayload]] =
        new TestSubscriber(msg => Message.create(transform(msg)))

      override def atMostOnceSource(): Source[SubscriberPayload, _] = {

        val serializer = topicCall.messageSerializer().serializerForRequest()
        val deserializer = topicCall.messageSerializer().deserializer(serializer.protocol())

        // Create a source for all the tags, and merge them all together.
        // Then, send the flow through a serializer and deserializer, to simulate sending it over the wire.
        Source.from(topicProducer.tags).asScala.flatMapMerge(topicProducer.tags.size(), { tag =>
          topicProducer.readSideStream.apply(tag, Offset.NONE).asScala.map(_.first)
        }).map { message =>
          serializer.serialize(message)
        }.map { bytes =>
          deserializer.deserialize(bytes)
        }.map(transform).asJava
      }

      override def atLeastOnce(flow: Flow[SubscriberPayload, Done, _]): CompletionStage[Done] = {
        atMostOnceSource().via(flow).runWith(Sink.ignore(), materializer)
      }
    }
  }
}
