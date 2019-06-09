/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import akka.Done
import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.Partition
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
      Flow.fromFunction(doSomethingWithTheMessage)
    )
  //#subscribe-to-topic

  var lastObservedMessage: String = _

  private def doSomethingWithTheMessage(greetingMessage: GreetingMessage): Done = {
    lastObservedMessage = greetingMessage.message
    Done
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  override def foo: ServiceCall[NotUsed, String] = ServiceCall { req =>
    scala.concurrent.Future.successful(lastObservedMessage)
  }

  def subscribeWithMetadata = {
    //#subscribe-to-topic-with-metadata
    import com.lightbend.lagom.scaladsl.api.broker.Message
    import com.lightbend.lagom.scaladsl.broker.kafka.KafkaMetadataKeys

    helloService
      .greetingsTopic()
      .subscribe
      .withMetadata
      .atLeastOnce(
        Flow[Message[GreetingMessage]].map { msg =>
          val greetingMessage = msg.payload
          val messageKey      = msg.messageKeyAsString
          val kafkaHeaders    = msg.get(KafkaMetadataKeys.Headers)
          println(s"Message: $greetingMessage Key: $messageKey Headers: $kafkaHeaders")
          Done
        }
      )
    //#subscribe-to-topic-with-metadata

  }

  def skipMessages = {
    //#subscribe-to-topic-skip-messages
    helloService
      .greetingsTopic()
      .subscribe
      .atLeastOnce(
        Flow[GreetingMessage].map {
          case msg @ GreetingMessage("Kia ora") => doSomethingWithTheMessage(msg)
          case _                                => Done // Skip all messages where the message is not "Kia ora".
        }
      )
    //#subscribe-to-topic-skip-messages
  }
}
