package docs.scaladsl.mb

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Message

//#inject-service
class AnotherServiceImpl(helloService: HelloService) extends AnotherService {
//#inject-service
  
  //#subscribe-to-topic
  helloService
    .greetingsTopic()
    .subscribe // <-- you get back a Subscriber instance
    .atLeastOnce(
      Flow[GreetingMessage].map { msg =>
        // Do somehting with the `msg`
        doSomethingWithTheMessage(msg)
        Done
      }
    )
  //#subscribe-to-topic

  var lastObservedMessage: String = _
  private def doSomethingWithTheMessage(greetingMessage: GreetingMessage) = {
    lastObservedMessage = greetingMessage.message
  }
  import scala.concurrent.ExecutionContext.Implicits.global

  override def foo: ServiceCall[NotUsed, String] = ServiceCall {
    req => scala.concurrent.Future.successful(lastObservedMessage)
  }

  def subscribeWithMetadata = {
    //#subscribe-to-topic-with-metadata
    import com.lightbend.lagom.scaladsl.api.broker.Message
    import com.lightbend.lagom.scaladsl.broker.kafka.KafkaMetadataKeys

    helloService.greetingsTopic()
      .subscribe.withMetadata
      .atLeastOnce(
        Flow[Message[GreetingMessage]].map { msg =>
          val greetingMessage = msg.payload
          val messageKey = msg.messageKeyAsString
          val kafkaHeaders = msg.get(KafkaMetadataKeys.Headers)
          println(s"Message: $greetingMessage Key: $messageKey Headers: $kafkaHeaders")
          Done
        }
      )
    //#subscribe-to-topic-with-metadata

  }
}

