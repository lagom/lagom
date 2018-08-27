/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit

import java.util.concurrent.TimeUnit

import akka.Done
import akka.persistence.query.{ NoOffset, Offset }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.lightbend.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.scaladsl.api.broker.{ TopicFactory, TopicFactoryProvider }
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service }
import com.lightbend.lagom.scaladsl.api.ServiceSupport.ScalaMethodTopic
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.{ Message, Subscriber, Topic }
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.spi.persistence.{ OffsetDao, OffsetStore }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.FiniteDuration

private[lagom] trait BaseTestTopicComponents extends TopicFactoryProvider {
  def lagomServer: LagomServer

  def materializer: Materializer

  override def optionalTopicFactory: Option[TopicFactory] = Some(topicFactory)

  override def topicPublisherName: Option[String] = super.topicPublisherName match {
    case Some(other) =>
      sys.error(s"Cannot provide the test topic factory as the default topic publisher since a default topic publisher has already been mixed into this cake: $other")
    case None => Some("test")
  }

  def topicFactory: TopicFactory
}

trait OffsetAwareTestTopicComponents extends BaseTestTopicComponents {

  def offsetStore: OffsetStore

  val offsetStoreInitTimeout: FiniteDuration = new FiniteDuration(2, TimeUnit.SECONDS)

  private def offsetDaoFactory(topic: Descriptor.TopicCall[_]): OffsetDao = {
    Await.result(offsetStore.prepare(s"topicProducer-${topic.topicId.name}", "test"), offsetStoreInitTimeout)
  }

  lazy val topicFactory: TopicFactory = new TestTopicFactory(lagomServer, offsetDaoFactory _)(materializer)

}

trait TestTopicComponents extends BaseTestTopicComponents {

  private val offsetDao = new OffsetDao {

    override def saveOffset(offset: Offset): Future[Done] = Future.successful(Done)

    override val loadedOffset: Offset = NoOffset
  }

  lazy val topicFactory: TopicFactory = new TestTopicFactory(lagomServer, _ => offsetDao)(materializer)

}

private[lagom] class TestTopicFactory(lagomServer: LagomServer, offsetDaoFactory: Descriptor.TopicCall[_] => OffsetDao)(implicit materializer: Materializer) extends TopicFactory {

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
              case topicProducer: TaggedOffsetTopicProducer[Message, _] =>
                val offsetDao = offsetDaoFactory(topicCall)
                new TestTopic(topicCall, topicProducer, offsetDao)(materializer)
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

private[lagom] class TestTopic[Payload, Event <: AggregateEvent[Event]](
  topicCall:     TopicCall[Payload],
  topicProducer: TaggedOffsetTopicProducer[Payload, Event],
  offsetDao:     OffsetDao
)(implicit materializer: Materializer) extends Topic[Payload] {

  import materializer.executionContext

  override def topicId: TopicId = topicCall.topicId

  override def subscribe: Subscriber[Payload] = new TestSubscriber[Payload](identity)

  private class TestSubscriber[WrappedPayload](transform: Payload => WrappedPayload) extends Subscriber[WrappedPayload] {

    override def withGroupId(groupId: String): Subscriber[WrappedPayload] = this

    override def withMetadata = new TestSubscriber[Message[WrappedPayload]](transform.andThen(Message.apply))

    override def atMostOnceSource: Source[WrappedPayload, _] = {
      val serializer = topicCall.messageSerializer
      Source(topicProducer.tags).flatMapMerge(topicProducer.tags.size, { tag =>
        topicProducer.readSideStream.apply(tag, offsetDao.loadedOffset)
      }).map {
        case (evt, offset) =>
          (serializer.serializerForRequest.serialize(evt), evt, offset)
      }.map {
        case (bytes, evt, offset) =>
          (serializer.deserializer(serializer.acceptResponseProtocols.head).deserialize(bytes), evt, offset)
      }.flatMapMerge(topicProducer.tags.size, r => {
        r match {
          case (deser, _, offset) => Source.fromFuture(offsetDao.saveOffset(offset).map(_ => transform(deser)))
        }
      })
    }

    override def atLeastOnce(flow: Flow[WrappedPayload, Done, _]): Future[Done] =
      atMostOnceSource.via(flow).runWith(Sink.ignore)
  }

}
