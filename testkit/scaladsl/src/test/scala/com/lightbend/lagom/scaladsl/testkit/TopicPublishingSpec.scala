/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit

import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.services.AlphaEvent
import com.lightbend.lagom.scaladsl.testkit.services.AlphaService
import org.scalatest.AsyncWordSpec
import org.scalatest.Matchers
import play.api.libs.ws.ahc.AhcWSComponents

abstract class AlphaApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with CassandraPersistenceComponents
    with TestTopicComponents
    with AhcWSComponents {
  override lazy val lagomServer = serverFor[AlphaService](new AlphaServiceImpl())

  override lazy val jsonSerializerRegistry = EmptyJsonSerializerRegistry
}

class AlphaServiceImpl extends AlphaService {
  override def messages: Topic[AlphaEvent] =
    TopicProducer.singleStreamWithOffset { offset =>
      Source(1 to 10).map(i => {
        if (i % 2 == 0) {
          (Some(AlphaEvent.apply(i)), Offset.sequence(i))
        } else {
          (None, Offset.sequence(i))
        }
      })
    }
}

class TopicPublishingSpec extends AsyncWordSpec with Matchers {
  "The AlphaService" should {
    "publish events on alpha topic" in ServiceTest.withServer(ServiceTest.defaultSetup.withCluster()) { ctx =>
      new AlphaApplication(ctx) with LocalServiceLocator
    } { server =>
      implicit val system = server.actorSystem
      implicit val mat    = server.materializer

      val client: AlphaService = server.serviceClient.implement[AlphaService]
      val source               = client.messages.subscribe.atMostOnceSource

      source
        .runWith(TestSink.probe[AlphaEvent])
        .request(5)
        .expectNextN(5) should ===(Seq(AlphaEvent(2), AlphaEvent(4), AlphaEvent(6), AlphaEvent(8), AlphaEvent(10)))
    }
  }
}
