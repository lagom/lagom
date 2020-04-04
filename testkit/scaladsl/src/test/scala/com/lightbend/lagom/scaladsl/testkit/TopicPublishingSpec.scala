/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit

import akka.actor.ActorSystem
import akka.persistence.query.Offset
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.lightbend.lagom.scaladsl.api.broker.Message
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.services.AlphaEvent
import com.lightbend.lagom.scaladsl.testkit.services.AlphaService
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.libs.ws.ahc.AhcWSComponents

abstract class AlphaApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with CassandraPersistenceComponents
    with TestTopicComponents
    with AhcWSComponents {
  override lazy val lagomServer: LagomServer =
    serverFor[AlphaService](new AlphaServiceImpl())

  override lazy val jsonSerializerRegistry: JsonSerializerRegistry =
    EmptyJsonSerializerRegistry
}

class AlphaServiceImpl extends AlphaService {
  override def messages: Topic[AlphaEvent] =
    TopicProducer.singleStreamWithOffset { offset =>
      val events = (1 to 10).filter(_ % 2 == 0).map(AlphaEvent.apply)
      Source(events).map(event => (event, Offset.sequence(event.message / 2)))
    }

  override def messagesWithMetadata: Topic[AlphaEvent] =
    TopicProducer.singleStreamWithOffsetAndMetadata { offset =>
      val messages = (1 to 10).filter(_ % 2 == 0).map(AlphaEvent.withMetadata)
      Source(messages).map(message => (message, Offset.sequence(message.payload.message / 2)))
    }
}

class TopicPublishingSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  private lazy val server =
    ServiceTest.startServer(ServiceTest.defaultSetup.withCluster()) { ctx =>
      new AlphaApplication(ctx) with LocalServiceLocator
    }

  private lazy val client: AlphaService =
    server.serviceClient.implement[AlphaService]

  "The AlphaService" should {

    implicit val system: ActorSystem = server.actorSystem
    implicit val mat: Materializer   = server.materializer

    "publish events on alpha topic" in {

      val source = client.messages.subscribe.atMostOnceSource

      source
        .runWith(TestSink.probe[AlphaEvent])
        .request(1)
        .expectNext should ===(AlphaEvent(2))
    }
    "publish events on alpha topic with metadata" in {

      val source =
        client.messagesWithMetadata.subscribe.withMetadata.atMostOnceSource

      source
        .runWith(TestSink.probe[Message[AlphaEvent]])
        .request(1)
        .expectNext should ===(AlphaEvent.withMetadata(2))
    }
  }

  protected override def beforeAll(): Unit = server

  protected override def afterAll(): Unit = server.stop()
}
