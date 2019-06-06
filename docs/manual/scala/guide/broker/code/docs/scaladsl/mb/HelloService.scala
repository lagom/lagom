/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import akka.Done
import akka.NotUsed

//#hello-service
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import play.api.libs.json.Format
import play.api.libs.json.Json

object HelloService {
  val TOPIC_NAME = "greetings"
}
trait HelloService extends Service {

  final override def descriptor = {
    import Service._
    named("brokerdocs")
      .withCalls(
        pathCall("/api/hello/:id", hello _),
        pathCall("/api/hello/:id", useGreeting _)
      )
      .withTopics(
        topic(HelloService.TOPIC_NAME, greetingsTopic)
      )
      .withAutoAcl(true)
  }

  // The topic handle
  def greetingsTopic(): Topic[GreetingMessage]

  def hello(id: String): ServiceCall[NotUsed, String]
  def useGreeting(id: String): ServiceCall[GreetingMessage, Done]
}
//#hello-service

case class GreetingMessage(message: String)

object GreetingMessage {
  implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
}
