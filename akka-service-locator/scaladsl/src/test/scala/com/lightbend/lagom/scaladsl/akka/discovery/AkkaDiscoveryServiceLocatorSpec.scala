/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.akka.discovery

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest._
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class AkkaDiscoveryServiceLocatorSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll with OptionValues {
  "ServiceLocator" should {
    "retrieve registered services" in {
      val serviceLocator = server.application.serviceLocator

      serviceLocator.locate("fake-service").map { res =>
        res.value.toString shouldBe "http://fake-service-host:9119"
      }
    }
  }

  private val server = ServiceTest.startServer(ServiceTest.defaultSetup) { ctx =>
    new LagomTestApplication(ctx)
  }

  protected override def afterAll() = server.stop()

  class LagomTestApplication(ctx: LagomApplicationContext)
      extends LagomApplication(ctx)
      with AhcWSComponents
      with AkkaDiscoveryComponents {
    override def lagomServer: LagomServer = serverFor[TestService](new TestServiceImpl)
  }

  trait TestService extends Service {
    def hello(name: String): ServiceCall[NotUsed, String]

    override def descriptor = {
      import Service._
      named("test-service")
        .withCalls(
          pathCall("/hello/:name", hello _)
        )
        .withAutoAcl(true)
    }
  }

  class TestServiceImpl extends TestService {
    override def hello(name: String) = ServiceCall { _ =>
      Future.successful(s"Hello $name")
    }
  }
}
