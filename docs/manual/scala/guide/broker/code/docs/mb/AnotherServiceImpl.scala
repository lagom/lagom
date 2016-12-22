package docs.mb

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
        Done
      }
    )
  //#subscribe-to-topic

  private def doSomethingWithTheMessage(greetingMessage: GreetingMessage) = ???

  override def foo: ServiceCall[NotUsed, NotUsed] = ???
}

