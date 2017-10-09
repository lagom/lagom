/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.lightbend.lagom.internal.scaladsl.server.ScaladslServiceRouter
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.transport.{ HeaderFilter, Method, NotFound, ResponseHeader }
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall, transport }
import org.scalatest.{ AsyncFlatSpec, BeforeAndAfterAll, Matchers }
import play.api.http.HttpConfiguration
import play.api.mvc
import play.api.mvc._
import play.api.test.FakeRequest

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
    // this test is canary
    val hardcodedResponse = "a response"
    val service = new AlphaService {
      override def simpleGet(): ServiceCall[NotUsed, String] = ServiceCall { _ =>
        Future.successful(hardcodedResponse)
      }
    }

    val x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result] = { (action) => (rh) => action(rh).run() }

    runRequest(service)(x) {
      _ should be(mvc.Results.Ok(hardcodedResponse))
    }
  }

  it should "serve a request with a Play Filter." in {
    // this test makes sure headers in request and response are added and they are added in the appropriate order.
    // This test only uses Play filters.
    val atomicInt = new AtomicInteger(0)
    val hardcodedResponse = "a response"
    val service = new AlphaService {
      override def simpleGet(): ServerServiceCall[NotUsed, String] = ServerServiceCall { (reqHeader, _) =>
        reqHeader
          .getHeader(PlayFilter.addedOnRequest)
          .map { value => Future.successful(value should be("1")) }
          .getOrElse(Future.failed(NotFound(s"Missing header ${PlayFilter.addedOnRequest}")))
          .map { _ =>
            (ResponseHeader.Ok.withHeader("in-service", atomicInt.incrementAndGet().toString), hardcodedResponse)
          }
      }
    }

    val x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result] = {
      (action) => new PlayFilter(atomicInt, mat).apply(rh => action(rh).run())
    }

    runRequest(service)(x) {
      _ should be(
        mvc.Results.Ok(hardcodedResponse)
          .withHeaders(
            ("in-service", "2"),
            (PlayFilter.addedOnResponse, "3")
          )
      )
    }
  }

  it should "serve a request with a Play Filter and a Lagom HeaderFilter invoking play first." in {
    // this test makes sure headers in request and response are added and they are added in the appropriate order.
    val atomicInt = new AtomicInteger(0)
    val hardcodedResponse = "a response"
    val service = new BetaService(atomicInt) {
      override def simpleGet(): ServerServiceCall[NotUsed, String] = ServerServiceCall { (reqHeader, _) =>
        reqHeader
          .getHeader(PlayFilter.addedOnRequest)
          .flatMap { p =>
            reqHeader.getHeader(LagomFilter.addedOnRequest).map { l => (p, l) }
          }
          .map { value => Future.successful(value should be(("1", "2"))) }
          .getOrElse(Future.failed(NotFound(s"Missing header ${PlayFilter.addedOnRequest}")))
          .map { _ =>
            (ResponseHeader.Ok.withHeader("in-service", atomicInt.incrementAndGet().toString), hardcodedResponse)
          }
      }
    }

    val x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result] = {
      (action) => new PlayFilter(atomicInt, mat).apply(rh => action(rh).run())
    }

    runRequest(service)(x) {
      _ should be(
        mvc.Results.Ok(hardcodedResponse)
          .withHeaders(
            ("in-service", "3"),
            (LagomFilter.addedOnResponse, "4"),
            (PlayFilter.addedOnResponse, "5")
          )
      )
    }
  }

  // ---------------------------------------------------------------------------------------------------

  private def runRequest[T](service: Service)(x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result])(block: mvc.Result => T): Future[T] = {
    val httpConfig = HttpConfiguration.createWithDefaults()
    val router = new ScaladslServiceRouter(service.descriptor, service, httpConfig)
    val req: mvc.Request[NotUsed] = FakeRequest(method = "GET", path = AlphaService.PATH).withBody[NotUsed](null)
    val reqHeader: mvc.RequestHeader = req
    val handler = router.routes(reqHeader)
    val futureResult: Future[mvc.Result] = handler match {
      case action: mvc.EssentialAction => x(action)(reqHeader)
      case _                           => Future.failed(NotFound("Not an EssentialAction."))
    }
    futureResult map block
  }
}

// ------------------------------------------------------------------------------------------------------------
// This is a play filter that adds a header on the request and the adds a header on the response. Headers may only
// be added once so invoking this Filter twice breaks the test.
class PlayFilter(atomicInt: AtomicInteger, mt: Materializer)(implicit ctx: ExecutionContext) extends Filter {

  import PlayFilter._

  override implicit def mat: Materializer = mt

  override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    ensureMissing(rh.headers.toSimpleMap, addedOnRequest)
    val richerHeaders = rh.headers.add(addedOnRequest -> atomicInt.incrementAndGet().toString)
    val richerRequest = rh.withHeaders(richerHeaders)
    f(richerRequest).map {
      case result =>
        ensureMissing(result.header.headers, addedOnResponse)
        result.withHeaders(addedOnResponse -> atomicInt.incrementAndGet().toString)
    }
  }

  private def ensureMissing(headers: Map[String, String], key: String) =
    if (headers.get(key).isDefined) throw NotFound(s"Header $key already exists.")
}

object PlayFilter {
  val addedOnRequest = "addedOnRequest-play"
  val addedOnResponse = "addedOnResponse-play"
}

// --------------------------------------------------------------------------------------------------
// A simple service tests may implement to provide their needed behavior.
trait AlphaService extends Service {
  override def descriptor: Descriptor =
    named("alpha").withCalls(restCall(Method.GET, AlphaService.PATH, simpleGet _))

  def simpleGet(): ServiceCall[NotUsed, String]
}

object AlphaService {
  val PATH = "/alpha"
}

// --------------------------------------------------------------------------------------------------
// Extends Alpha adding HeaderFilters
abstract class BetaService(atomicInteger: AtomicInteger) extends AlphaService {
  override def descriptor: Descriptor =
    super.descriptor.withHeaderFilter(new LagomFilter)

  class LagomFilter extends HeaderFilter {
    override def transformServerRequest(request: transport.RequestHeader): transport.RequestHeader = {
      request.addHeader(LagomFilter.addedOnRequest, atomicInteger.incrementAndGet().toString)
    }

    override def transformServerResponse(response: ResponseHeader, request: transport.RequestHeader): ResponseHeader = {
      response.addHeader(LagomFilter.addedOnResponse, atomicInteger.incrementAndGet().toString)
    }

    override def transformClientResponse(response: ResponseHeader, request: transport.RequestHeader): ResponseHeader = ???

    override def transformClientRequest(request: transport.RequestHeader): transport.RequestHeader = ???
  }

}

object LagomFilter {
  val addedOnRequest = "addedOnRequest-Lagom"
  val addedOnResponse = "addedOnResponse-Lagom"
}
