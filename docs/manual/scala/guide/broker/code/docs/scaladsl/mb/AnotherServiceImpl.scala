package docs.scaladsl.mb

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.api.ServiceCall

//#inject-service
class AnotherServiceImpl(helloService: HelloService) extends AnotherService {
//#inject-service
  
  //#subscribe-to-topic
  helloService
    .greetingsTopic()
    .subscribe // <-- you get back a Subscriber instance
    .atLeastOnce(
      Flow[GreetingMessage].map{ msg => 
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
}

