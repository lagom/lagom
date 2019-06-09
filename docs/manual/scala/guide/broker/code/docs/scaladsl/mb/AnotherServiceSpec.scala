/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import akka.Done
import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ProducerStubFactory
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import com.lightbend.lagom.scaladsl.testkit._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.api.libs.ws.ahc.AhcWSComponents

abstract class AnotherApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with AhcWSComponents {

  lazy val helloService = serviceClient.implement[HelloService]

  override lazy val lagomServer = serverFor[AnotherService](new AnotherServiceImpl(helloService))

}

//#topic-test-consuming-from-a-topic
class AnotherServiceSpec extends WordSpec with Matchers with Eventually with ScalaFutures {
  var producerStub: ProducerStub[GreetingMessage] = _

  "The AnotherService" should {
    "publish updates on greetings message" in
      ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
        new AnotherApplication(ctx) with LocalServiceLocator {

          // (1) creates an in-memory topic and binds it to a producer stub
          val stubFactory = new ProducerStubFactory(actorSystem, materializer)
          producerStub = stubFactory.producer[GreetingMessage](HelloService.TOPIC_NAME)

          // (2) Override the default Hello service with our service stub
          // which gets the producer stub injected
          override lazy val helloService = new HelloServiceStub(producerStub)
        }
      } { server =>
        // (3) produce a message in the stubbed topic via it's producer
        producerStub.send(GreetingMessage("Hi there!"))

        // create a service client to assert the message was consumed
        eventually(timeout(Span(5, Seconds))) {
          // cannot use async specs here because eventually only detects raised exceptions to retry.
          // if a future fail at the first time, eventually won't retry though future will succeed later.
          // see https://github.com/lagom/lagom/issues/876 for detail info.
          val futureResp = server.serviceClient.implement[AnotherService].foo.invoke()
          whenReady(futureResp) { resp =>
            resp should ===("Hi there!")
          }
        }
      }
  }
}

// (2) a Service stub that will use the in-memoru topic bound to
// our producer stub
class HelloServiceStub(stub: ProducerStub[GreetingMessage]) extends HelloService {
  override def greetingsTopic(): Topic[GreetingMessage] = stub.topic

  override def hello(id: String): ServiceCall[NotUsed, String] = ???

  override def useGreeting(id: String): ServiceCall[GreetingMessage, Done] = ???
}

//#topic-test-consuming-from-a-topic
