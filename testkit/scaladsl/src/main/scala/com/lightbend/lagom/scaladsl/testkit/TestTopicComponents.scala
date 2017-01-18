/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import akka.Done
import akka.persistence.query.Offset
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.lightbend.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.scaladsl.api.broker.{TopicFactory, TopicFactoryProvider}
import com.lightbend.lagom.scaladsl.api.Descriptor.{TopicCall, TopicHolder}
import com.lightbend.lagom.scaladsl.api.ServiceSupport.ScalaMethodTopic
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.{Subscriber, Topic}
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.server.LagomServer

import scala.concurrent.Future

trait TestTopicComponents extends TopicFactoryProvider {
  def lagomServer: LagomServer

  def materializer: Materializer

  override def optionalTopicFactory: Option[TopicFactory] = Some(topicFactory)

  lazy val topicFactory: TopicFactory = new TestTopicFactory(lagomServer)(materializer)

}

private[lagom] class TestTopicFactory(lagomServer: LagomServer)(implicit materializer: Materializer) extends TopicFactory {

  private val topics: Map[TopicId, TopicHolder] =
    lagomServer.serviceBindings.flatMap { binding =>
      binding.descriptor.topics.map { topic =>
        topic.topicId -> topic.topicHolder
      }
    }.toMap

  override def create[Message](topicCall: TopicCall[Message]): Topic[Message] =
    topics.get(topicCall.topicId) match {
      case Some(topicHolder) =>
        topicHolder match {
          case method: ScalaMethodTopic[Message] =>
            ???
          case _ =>
            throw new IllegalArgumentException(s"Testkit does not know how to handle topic ${topicCall.topicId}")
        }

        new TestTopic[Message](topicHolder)
      case None =>
        throw new IllegalArgumentException(s"${topicCall.topicId} has'nt been resolved")
    }
}

private[lagom] class TestTopic[Message, Event <: AggregateEvent[Event]](topicProducer: TaggedOffsetTopicProducer[Message, Event]
                                                                       )(implicit materializer: Materializer) extends Topic[Message] {
  override def subscribe: Subscriber[Message] = new TestSubscriber[Message](topicProducer)
}

private[lagom] class TestSubscriber[Message, Event <: AggregateEvent[Event]](topicProducer: TaggedOffsetTopicProducer[Message, Event]
                                                                            )(implicit materializer: Materializer) extends Subscriber[Message] {
  override def withGroupId(groupId: String): Subscriber[Message] = this

  override def atMostOnceSource: Source[Message, _] = {
    //TODO: test de/ser
    Source(topicProducer.tags).flatMapMerge(topicProducer.tags.size, { tag =>
      topicProducer.readSideStream.apply(tag, Offset.noOffset).map(_._1)
    })
  }

  override def atLeastOnce(flow: Flow[Message, Done, _]): Future[Done] =
    atMostOnceSource.via(flow).runWith(Sink.ignore)
}
