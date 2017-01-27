/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import akka.Done
import akka.persistence.query.Offset
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.lightbend.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.scaladsl.api.broker.{ TopicFactory, TopicFactoryProvider }
import com.lightbend.lagom.scaladsl.api.Descriptor.{ TopicCall, TopicHolder }
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceSupport.ScalaMethodTopic
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.{ Subscriber, Topic }
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.server.{ LagomServer, LagomServiceBinding }

import scala.concurrent.Future

trait TestTopicComponents extends TopicFactoryProvider {
  def lagomServer: LagomServer

  def materializer: Materializer

  override def optionalTopicFactory: Option[TopicFactory] = Some(topicFactory)

  override def topicPublisherName: Option[String] = super.topicPublisherName match {
    case Some(other) =>
      sys.error(s"Cannot provide the test topic factory as the default topic publisher since a default topic publisher has already been mixed into this cake: $other")
    case None => Some("test")
  }

  lazy val topicFactory: TopicFactory = new TestTopicFactory(lagomServer)(materializer)

}

private[lagom] class TestTopicFactory(lagomServer: LagomServer)(implicit materializer: Materializer) extends TopicFactory {

  private val topics: Map[TopicId, Service] =
    lagomServer.serviceBindings.flatMap { binding =>
      binding.descriptor.topics.map { topic =>
        topic.topicId -> binding.service.asInstanceOf[Service]
      }
    }.toMap

  override def create[Message](topicCall: TopicCall[Message]): Topic[Message] =
    topics.get(topicCall.topicId) match {
      case Some(service) =>
        topicCall.topicHolder match {
          case method: ScalaMethodTopic[Message] =>
            method.method.invoke(service) match {
              case topicProducer: TaggedOffsetTopicProducer[Message, _] => new TestTopic(topicCall, topicProducer)(materializer)
              case _ =>
                throw new IllegalArgumentException(s"Testkit does not know how to handle the topic type for ${topicCall.topicId}")
            }
          case _ =>
            throw new IllegalArgumentException(s"Testkit does not know how to handle topic ${topicCall.topicId}")
        }
      case None =>
        throw new IllegalArgumentException(s"${topicCall.topicId} hasn't been resolved")
    }
}

private[lagom] class TestTopic[Message, Event <: AggregateEvent[Event]](
  topicCall:     TopicCall[Message],
  topicProducer: TaggedOffsetTopicProducer[Message, Event]
)(implicit materializer: Materializer) extends Topic[Message] {

  override def topicId: TopicId = topicCall.topicId

  override def subscribe: Subscriber[Message] = new Subscriber[Message] {
    override def withGroupId(groupId: String): Subscriber[Message] = this

    override def atMostOnceSource: Source[Message, _] = {

      val serializer = topicCall.messageSerializer
      Source(topicProducer.tags).flatMapMerge(topicProducer.tags.size, { tag =>
        topicProducer.readSideStream.apply(tag, Offset.noOffset).map(_._1)
      }).map { evt =>
        serializer.serializerForRequest.serialize(evt)
      }.map { bytes =>
        serializer.deserializer(serializer.acceptResponseProtocols.head).deserialize(bytes)
      }
    }

    override def atLeastOnce(flow: Flow[Message, Done, _]): Future[Done] =
      atMostOnceSource.via(flow).runWith(Sink.ignore)
  }

}