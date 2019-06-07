/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.services

package implementhelloclient {

  import helloservice.HelloService

  import com.lightbend.lagom.scaladsl.server.LagomApplication
  import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
  import play.api.libs.ws.ahc.AhcWSComponents

  //#implement-hello-client
  abstract class MyApplication(context: LagomApplicationContext)
      extends LagomApplication(context)
      with AhcWSComponents {

    lazy val helloService = serviceClient.implement[HelloService]
  }
  //#implement-hello-client

}

package helloconsumer {

  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import helloservice.HelloService

  import scala.concurrent.ExecutionContext
  import scala.concurrent.Future

  trait MyService extends Service {
    def sayHelloLagom: ServiceCall[NotUsed, String]

    override def descriptor = {
      import Service._

      named("myservice").withCalls(call(sayHelloLagom))
    }
  }

  //#hello-consumer
  class MyServiceImpl(helloService: HelloService)(implicit ec: ExecutionContext) extends MyService {

    override def sayHelloLagom = ServiceCall { _ =>
      val result: Future[String] =
        helloService.sayHello.invoke("Lagom")

      result.map { response =>
        s"Hello service said: $response"
      }
    }
  }
  //#hello-consumer
}

package circuitbreakers {

  import com.lightbend.lagom.scaladsl.api.Descriptor
  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall

  trait HelloServiceWithCircuitBreaker extends Service {
    def sayHi: ServiceCall[String, String]

    def hiAgain: ServiceCall[String, String]

    // @formatter:off
    //#circuit-breaker
    import com.lightbend.lagom.scaladsl.api.CircuitBreaker

    def descriptor: Descriptor = {
      import Service._

      named("hello").withCalls(
        namedCall("hi", this.sayHi),
        namedCall("hiAgain", this.hiAgain)
          .withCircuitBreaker(CircuitBreaker.identifiedBy("hello2"))
      )
    }
    //#circuit-breaker
    // @formatter:on
  }

}
