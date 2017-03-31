/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit.services

import java.util.concurrent.ConcurrentLinkedQueue

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.playjson.{ JsonSerializer, JsonSerializerRegistry }
import com.lightbend.lagom.scaladsl.server.{ LagomApplication, LagomApplicationContext }
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.immutable.Seq
import scala.concurrent.Future

object AlphaService {
  val TOPIC_ID = "alpha-topic-name"
}

trait AlphaService extends Service {

  import AlphaService.TOPIC_ID

  override def descriptor: Descriptor = {
    named("alpha")
      .addTopics(
        topic(TOPIC_ID, messages)
      )
  }

  def messages: Topic[AlphaEvent]
}

case class AlphaEvent(message: Int)

object AlphaEvent {

  import play.api.libs.json._

  implicit val format: Format[AlphaEvent] = Json.format[AlphaEvent]
}

// ------------------------------------------------------
object FakesSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = List(JsonSerializer[AlphaEvent], JsonSerializer[ReceivedMessage])
}

// ------------------------------------------------------

abstract class DownstreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with AhcWSComponents {

  lazy val alphaService = serviceClient.implement[AlphaService]

  override lazy val lagomServer = serverFor[CharlieService](new CharlieServiceImpl(alphaService))

}

trait CharlieService extends Service {
  override def descriptor: Descriptor = {
    named("beta")
      .addCalls(
        namedCall("messages", messages)
      )
  }

  def messages: ServiceCall[NotUsed, Seq[ReceivedMessage]]
}

case class ReceivedMessage(topicId: String, msg: Int)

object ReceivedMessage {

  import play.api.libs.json._

  implicit val format: Format[ReceivedMessage] = Json.format[ReceivedMessage]
}

class CharlieServiceImpl(alpha: AlphaService) extends CharlieService {
  private val receivedMessages = new ConcurrentLinkedQueue[ReceivedMessage]

  alpha.messages.subscribe.atLeastOnce(
    Flow.fromFunction[AlphaEvent, Done](ae => {
      receivedMessages.add(ReceivedMessage("A", ae.message))
      akka.Done
    })
  )

  override def messages: ServiceCall[NotUsed, Seq[ReceivedMessage]] = ServiceCall { _ =>
    Future.successful {
      import collection.JavaConverters._
      Seq(receivedMessages.asScala.toSeq: _*)
    }
  }
}
