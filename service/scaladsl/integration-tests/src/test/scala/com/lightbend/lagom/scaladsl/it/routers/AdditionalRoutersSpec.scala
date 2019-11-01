/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.it.routers

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import com.lightbend.lagom.scaladsl.testkit.ServiceTest.TestServer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.api.http.DefaultWriteables
import play.api.http.HeaderNames
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc
import play.api.mvc._
import play.api.routing.SimpleRouterImpl
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.core.j.JavaRouterAdapter
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AdditionalRoutersSpec extends WordSpec with Matchers with ScalaFutures {
  "A LagomServer " should {
    "be extensible with a Play Router" in withServer { server =>
      val request = FakeRequest(GET, "/hello/")
      val result  = Helpers.route(server.application.application, request).get.futureValue

      result.header.status shouldBe OK
      val body = result.body.consumeData(server.materializer).futureValue.utf8String
      body shouldBe "hello"
    }
  }

  def withServer(block: TestServer[TestApp] => Unit): Unit = {
    ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra(false).withCluster(false)) { ctx =>
      new TestApp(ctx)
    } { server =>
      block(server)
    }
  }

  class TestApp(context: LagomApplicationContext)
      extends LagomApplication(context)
      with AhcWSComponents
      with LocalServiceLocator {
    override def lagomServer: LagomServer =
      serverFor[AdditionalRoutersService](new AdditionalRoutersServiceImpl)
        .additionalRouter(FixedResponseRouter("hello").withPrefix("/hello"))
  }
}

/**
 * Builds a router that can be used in test.
 *
 * The router is configured with a fixed message and always respond with the same message.
 * @return
 */
object FixedResponseRouter {
  def apply(msg: String) =
    new SimpleRouterImpl({
      case _ =>
        new Action[Unit] {
          override def parser: BodyParser[Unit] = mvc.BodyParsers.utils.empty

          override def apply(request: Request[Unit]): Future[Result] =
            Future.successful(Results.Ok(msg))

          override def executionContext: ExecutionContext =
            scala.concurrent.ExecutionContext.global
        }
    })
}
