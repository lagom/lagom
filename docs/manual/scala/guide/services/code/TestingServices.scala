package docs.scaladsl.services

package helloservicespec {

  import docs.scaladsl.services.helloservice.HelloService
  import docs.scaladsl.services.lagomapplication.HelloApplication

  //#hello-service-spec
  import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
  import com.lightbend.lagom.scaladsl.testkit.ServiceTest
  import org.scalatest.{AsyncWordSpec, Matchers}

  class HelloServiceSpec extends AsyncWordSpec with Matchers {

    "The HelloService" should {
      "say hello" in ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
        new HelloApplication(ctx) with LocalServiceLocator
      } { server =>
        val client = server.serviceClient.implement[HelloService]

        client.sayHello.invoke("Alice").map { response =>
          response should ===("Hello Alice!")
        }
      }
    }
  }
  //#hello-service-spec

}

package helloservicespecshared {

  import docs.scaladsl.services.helloservice.HelloService
  import docs.scaladsl.services.lagomapplication.HelloApplication

  //#hello-service-spec-shared
  import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
  import com.lightbend.lagom.scaladsl.testkit.ServiceTest
  import org.scalatest.{AsyncWordSpec, Matchers, BeforeAndAfterAll}

  class HelloServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

    lazy val server = ServiceTest.startServer(ServiceTest.defaultSetup) { ctx =>
      new HelloApplication(ctx) with LocalServiceLocator
    }
    lazy val client = server.serviceClient.implement[HelloService]

    "The HelloService" should {
      "say hello" in {
        client.sayHello.invoke("Alice").map { response =>
          response should ===("Hello Alice!")
        }
      }
    }

    override protected def beforeAll() = server

    override protected def afterAll() = server.stop()
  }
  //#hello-service-spec-shared
}

package stubservices {

  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
  import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
  import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
  import com.lightbend.lagom.scaladsl.testkit.ServiceTest

  import scala.concurrent.Future

  trait GreetingService extends Service {
    def greeting: ServiceCall[NotUsed, String]
    override def descriptor = {
      import Service._
      named("greeting").withCalls(call(greeting))
    }
  }

  abstract class HelloApplication(ctx: LagomApplicationContext) extends docs.scaladsl.services.lagomapplication.HelloApplication(ctx) {
    lazy val greetingService: GreetingService = serviceClient.implement[GreetingService]
  }

  class StubServices {

    //#stub-services
    lazy val server = ServiceTest.startServer(ServiceTest.defaultSetup) { ctx =>
      new HelloApplication(ctx) with LocalServiceLocator {

        override lazy val greetingService = new GreetingService {
          override def greeting = ServiceCall { _ =>
            Future.successful("Hello")
          }
        }
      }
    }
    //#stub-services
  }
}

package enablecassandra {

  import docs.scaladsl.services.lagomapplication.HelloApplication
  import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
  import com.lightbend.lagom.scaladsl.testkit.ServiceTest

  class HelloServiceSpec {

    //#enable-cassandra
    lazy val server = ServiceTest.startServer(
      ServiceTest.defaultSetup.withCassandra(true)
    ) { ctx =>
      new HelloApplication(ctx) with LocalServiceLocator
    }
    //#enable-cassandra
  }
}

package enablecluster {

  import docs.scaladsl.services.lagomapplication.HelloApplication
  import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
  import com.lightbend.lagom.scaladsl.testkit.ServiceTest

  class HelloServiceSpec {

    //#enable-cluster
    lazy val server = ServiceTest.startServer(
      ServiceTest.defaultSetup.withCluster(true)
    ) { ctx =>
      new HelloApplication(ctx) with LocalServiceLocator
    }
    //#enable-cluster
  }
}

package streamedservices {

  import akka.NotUsed
  import akka.stream.scaladsl.Source
  import akka.stream.testkit.scaladsl.TestSink
  import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
  import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomServer, LocalServiceLocator}
  import com.lightbend.lagom.scaladsl.testkit.ServiceTest
  import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}
  import play.api.libs.ws.ahc.AhcWSComponents

  import scala.concurrent.Future

  //#echo-service
  trait EchoService extends Service {
    def echo: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

    override def descriptor = {
      import Service._
      named("echo").withCalls(
        call(echo)
      )
    }
  }
  //#echo-service

  class EchoServiceImpl extends EchoService {
    override def echo = ServiceCall(Future.successful)
  }

  class EchoServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

    lazy val server = ServiceTest.startServer(ServiceTest.defaultSetup) { ctx =>
      new LagomApplication(ctx) with LocalServiceLocator with AhcWSComponents {
        override lazy val lagomServer = serverFor[EchoService](new EchoServiceImpl)
      }
    }
    lazy val client = server.serviceClient.implement[EchoService]

    import server.materializer

    //#echo-service-spec
    "The EchoService" should {
      "echo" in {
        // Use a source that never terminates (concat Source.maybe) so we
        // don't close the upstream, which would close the downstream
        val input = Source(List("msg1", "msg2", "msg3")).concat(Source.maybe)
        client.echo.invoke(input).map { output =>
          val probe = output.runWith(TestSink.probe(server.actorSystem))
          probe.request(10)
          probe.expectNext("msg1")
          probe.expectNext("msg2")
          probe.expectNext("msg3")
          probe.cancel
          succeed
        }
      }
    }
    //#echo-service-spec

    override protected def beforeAll() = server
    override protected def afterAll() = server.stop()
  }

}
