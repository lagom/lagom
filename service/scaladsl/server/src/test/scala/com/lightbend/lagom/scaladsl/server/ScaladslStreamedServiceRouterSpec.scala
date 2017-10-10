/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import com.lightbend.lagom.internal.scaladsl.server.ScaladslServiceRouter
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.api.{ Service, ServiceCall }
import com.lightbend.lagom.scaladsl.server.mocks._
import com.lightbend.lagom.scaladsl.server.testkit.FakeRequest
import org.scalatest.{ Assertion, AsyncFlatSpec, BeforeAndAfterAll, Matchers }
import play.api.http.HttpConfiguration
import play.api.http.websocket.{ Message, TextMessage }
import play.api.mvc
import play.api.mvc.Handler

import scala.concurrent.{ ExecutionContext, Future }

/**
 * This test relies on DefaultExceptionSerializer so in case of failure some information is lost on de/ser. Check the
 * status code of the response (won't be 200) and locate the suspect line of code where that status code is launched.
 */
class ScaladslStreamedServiceRouterSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  private val system = ActorSystem("ScaladslServiceRouterSpec")
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: Materializer = ActorMaterializer.create(system)

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  behavior of "ScaladslServiceRouter"

  it should "serve a non-filtered Streamed request" in {
    val atomicBoolean = new AtomicBoolean(false)
    // this test is canary
    val service = new SimpleStreamedService {
      override def streamed(): ServerServiceCall[Source[String, NotUsed], Source[String, NotUsed]] = ServerServiceCall { (headers, req) =>
        val responseHeader = ResponseHeader.Ok.withHeader("in-service", "value")
        atomicBoolean.compareAndSet(false, true)
        Future.successful((responseHeader, Source.single("23")))
      }
    }

    val x: mvc.WebSocket => mvc.RequestHeader => Future[WSFlow] =
      (websocket) => (rh) => websocket(rh).map(_.right.get)

    runRequest(service)(x) {
      atomicBoolean.get() should be(true)
    }
  }

  type WSFlow = Flow[Message, Message, _]

  // ---------------------------------------------------------------------------------------------------

  private def runRequest(service: Service)(x: mvc.WebSocket => mvc.RequestHeader => Future[WSFlow])(block: => Assertion): Future[Assertion] = {
    val httpConfig = HttpConfiguration.createWithDefaults()
    val router = new ScaladslServiceRouter(service.descriptor, service, httpConfig)
    val req: mvc.Request[NotUsed] = new FakeRequest(method = "GET", path = PathProvider.PATH)
    val reqHeader: mvc.RequestHeader = req
    val handler = router.routes(reqHeader)
    val futureResult: Future[WSFlow] = handler match {
      case action: mvc.WebSocket => x(action)(reqHeader)
      case _                     => Future.failed(PayloadTooLarge("Not a WebSocket."))
    }
    futureResult flatMap {
      _.runWith(Source.single(TextMessage("41")), Sink.ignore)._2.map {
        _ => block
      }
    }
  }
}

