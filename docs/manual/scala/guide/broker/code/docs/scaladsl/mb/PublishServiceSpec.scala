/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import com.lightbend.lagom.scaladsl.testkit.TestTopicComponents
import play.api.libs.ws.ahc.AhcWSComponents
import org.scalatest.AsyncWordSpec
import org.scalatest.Matchers
import akka.NotUsed
import akka.Done
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.TestSubscriber.Probe

abstract class PublishApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with AhcWSComponents {

  override lazy val lagomServer = serverFor[service.PublishService](new service.PublishServiceImpl())
}

package service {

  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.broker.TopicProducer

  object PublishService {
    val TOPIC_NAME = "events"
  }

  trait PublishService extends Service {
    final override def descriptor = {
      import Service._
      named("brokerdocs")
        .withTopics(topic(PublishService.TOPIC_NAME, events))
        .withAutoAcl(true)
    }

    def events(): Topic[PubMessage]
  }

  case class PubMessage(message: String)

  object PubMessage {
    import play.api.libs.json.Format
    import play.api.libs.json.Json
    implicit val format: Format[PubMessage] = Json.format[PubMessage]
  }

  class PublishServiceImpl() extends PublishService {
    override def events(): Topic[PubMessage] =
      TopicProducer.singleStreamWithOffset { offset =>
        Source((1 to 10)).map(i => (PubMessage(s"msg $i"), offset))
      }
  }

}

class PublishServiceSpec extends AsyncWordSpec with Matchers {

  import service._

  //#topic-test-publishing-into-a-topic
  "The PublishService" should {
    "publish events on the topic" in ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
      new PublishApplication(ctx) with LocalServiceLocator with TestTopicComponents
    } { server =>
      implicit val system = server.actorSystem
      implicit val mat    = server.materializer

      val client: PublishService = server.serviceClient.implement[PublishService]
      val source                 = client.events().subscribe.atMostOnceSource
      source
        .runWith(TestSink.probe[PubMessage])
        .request(1)
        .expectNext should ===(PubMessage("msg 1"))

    }
  }
  //#topic-test-publishing-into-a-topic
}
