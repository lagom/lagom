package docs.mb

import akka.{Done, NotUsed}

//#hello-service
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}

trait HelloService extends Service {

  override final def descriptor = {
    import Service._
    named("brokerdocs").withCalls(
      pathCall("/api/hello/:id", hello _),
      pathCall("/api/hello/:id", useGreeting _)
    ).withTopics(
      topic("greetings", greetingsTopic)
    ).withAutoAcl(true)
  }

  // The topic handle
  def greetingsTopic() : Topic [GreetingMessage]

  def hello(id: String): ServiceCall[NotUsed, String]
  def useGreeting(id: String): ServiceCall[GreetingMessage, Done]
}
//#hello-service


case class GreetingMessage(message: String)

object GreetingMessage {
  implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
}

