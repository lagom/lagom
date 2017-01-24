package docs.scaladsl.mb

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomServer, LocalServiceLocator}
import com.lightbend.lagom.scaladsl.testkit.{ProducerStubFactory, ServiceTest, _}
import play.api.libs.ws.ahc.AhcWSComponents
import org.scalatest.{AsyncWordSpec, Matchers}
import akka.{NotUsed, Done}

abstract class AnotherApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  lazy val helloService = serviceClient.implement[HelloService]

  override lazy val lagomServer = LagomServer.forServices(
    bindService[AnotherService].to(new AnotherServiceImpl(helloService))
  )
}

//#topic-test-consuming-from-a-topic
class AnotherServiceSpec extends AsyncWordSpec with Matchers {
  var producerStub: ProducerStub[GreetingMessage] = _

  "The AnotherService" should {
    "publish updates on greetings message" in
      ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
        new AnotherApplication(ctx) with LocalServiceLocator {

          // (1) creates an in-memory topic and binds it to a producer stub
          val stubFactory = new ProducerStubFactory(actorSystem, materializer)
          producerStub =
            stubFactory.producer[GreetingMessage](HelloService.TOPIC_NAME)

          // (2) Override the default Hello service with our service stub
          // which gets the producer stub injected
          override lazy val helloService = new HelloServiceStub(producerStub)
        }
      } { server =>

        // (3) produce a message in the stubbed topic via it's producer
        producerStub.send(GreetingMessage("Hi there!"))

        // create a service client to assert the message was consumed
        server.serviceClient.implement[AnotherService].foo.invoke().map { resp =>
          resp should ===("Hi there!")
        }

      }
  }
}

// (2) a Service stub that will use the in-memoru topic bound to
// our producer stub
class HelloServiceStub(stub: ProducerStub[GreetingMessage])
  extends HelloService {
  override def greetingsTopic(): Topic[GreetingMessage] = stub.topic

  override def hello(id: String): ServiceCall[NotUsed, String] = ???

  override def useGreeting(id: String): ServiceCall[GreetingMessage, Done] = ???
}

//#topic-test-consuming-from-a-topic


