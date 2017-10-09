/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import com.lightbend.lagom.internal.scaladsl.server.ScaladslServiceRouter
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import org.scalatest.{ AsyncFlatSpec, BeforeAndAfterAll, Matchers }
import play.api.http.HttpConfiguration
import play.api.libs.streams.Accumulator
import play.api.mvc
import play.api.mvc.{ AnyContentAsEmpty, Request, RequestHeader, WrappedRequest }
import play.api.test.FakeRequest
import play.mvc.Http
import play.mvc.Http.RequestBuilder

import scala.concurrent.{ ExecutionContext, Future }

/**
 *
 */
class ScaladslServiceRouterSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  private val system = ActorSystem("ScaladslServiceRouterSpec")
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: Materializer = ActorMaterializer.create(system)

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  behavior of "ScaladslServiceRouter"

  it should "serve a basic request" in {
    val hardcodedResponse = "a response"
    val httpConfig = HttpConfiguration.createWithDefaults()
    val service = new AlphaService {
      override def simpleGet(): ServiceCall[NotUsed, String] = ServiceCall { _ =>
        Future.successful(hardcodedResponse)
      }
    }

    val router = new ScaladslServiceRouter(service.descriptor, service, httpConfig)

    val req: mvc.Request[NotUsed] = FakeRequest(method = "GET", path = "/alpha").withBody[NotUsed](null)
    val reqHeader: mvc.RequestHeader = req
    val futureResult: Future[mvc.Result] = router.routes(reqHeader) match {
      case action: mvc.EssentialAction => action(reqHeader).run()
      case _                           => Future.failed(new RuntimeException("not an EssentialAction."))
    }

    futureResult map {
      _ should be(mvc.Results.Ok(hardcodedResponse))
    }
  }

}

trait AlphaService extends Service {

  override def descriptor: Descriptor = {
    named("alpha")
      .withCalls(
        restCall(Method.GET, "/alpha", simpleGet _)
      )
  }

  def simpleGet(): ServiceCall[NotUsed, String]
}
