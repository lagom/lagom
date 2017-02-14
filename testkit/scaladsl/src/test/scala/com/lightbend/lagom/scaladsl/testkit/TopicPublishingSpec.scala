/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.{ LagomApplication, LagomApplicationContext, LagomServer, LocalServiceLocator }
import com.lightbend.lagom.scaladsl.testkit.services.{ AlphaEvent, AlphaService }
import org.scalatest.{ AsyncWordSpec, Matchers }
import play.api.libs.ws.ahc.AhcWSComponents

abstract class AlphaApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with CassandraPersistenceComponents
  with TestTopicComponents
  with AhcWSComponents {

  override lazy val lagomServer = LagomServer.forServices(
    bindService[AlphaService].to(new AlphaServiceImpl())
  )

  override lazy val jsonSerializerRegistry = EmptyJsonSerializerRegistry
}

class AlphaServiceImpl extends AlphaService {
  override def messages: Topic[AlphaEvent] =
    TopicProducer.singleStreamWithOffset { offset =>
      val events = (1 to 10).filter(_ % 2 == 0).map(AlphaEvent.apply)
      Source(events).map(event => (event, Offset.sequence(event.message / 2)))
    }
}

class TopicPublishingSpec extends AsyncWordSpec with Matchers {

  "The AlphaService" should {
    "publish events on alpha topic" in ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
      new AlphaApplication(ctx) with LocalServiceLocator
    } { server =>

      implicit val system = server.actorSystem
      implicit val mat = server.materializer

      val client: AlphaService = server.serviceClient.implement[AlphaService]
      val source = client.messages.subscribe.atMostOnceSource

      source.runWith(TestSink.probe[AlphaEvent])
        .request(1)
        .expectNext should ===(AlphaEvent(2))

    }
  }

}
