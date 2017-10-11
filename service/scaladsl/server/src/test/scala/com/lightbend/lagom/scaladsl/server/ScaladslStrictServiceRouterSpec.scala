/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ ActorMaterializer, Materializer }
import com.lightbend.lagom.internal.scaladsl.server.ScaladslServiceRouter
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.api.{ Service, ServiceCall }
import com.lightbend.lagom.scaladsl.server.mocks._
import com.lightbend.lagom.scaladsl.server.testkit.FakeRequest
import org.scalatest.{ AsyncFlatSpec, BeforeAndAfterAll, Matchers }
import play.api.http.HttpConfiguration
import play.api.mvc
import play.api.mvc.{ EssentialAction, Handler, Results }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * This test relies on DefaultExceptionSerializer so in case of failure some information is lost on de/ser. Check the
 * status code of the response (won't be 200) and locate the suspect line of code where that status code is launched.
 */
class ScaladslStrictServiceRouterSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  private val system = ActorSystem("ScaladslServiceRouterSpec")
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: Materializer = ActorMaterializer.create(system)

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  behavior of "ScaladslServiceRouter"

  it should "serve a non-filtered Strict request" in {
    // this test is canary
    val hardcodedResponse = "a response"
    val service = new SimpleStrictService {
      override def simpleGet(): ServiceCall[NotUsed, String] = ServiceCall { _ =>
        Future.successful(hardcodedResponse)
      }
    }

    val x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result] = { (action) => (rh) => action(rh).run() }

    runRequest(service)(x) {
      _ should be(mvc.Results.Ok(hardcodedResponse))
    }
  }

  it should "propagate headers altered by a Play Filter down to the ServiceImpl. [String message]" in {
    // this test makes sure headers in request and response are added and they are added in the appropriate order.
    // This test only uses Play filters.
    val atomicInt = new AtomicInteger(0)
    val hardcodedResponse = "a response"
    val service = new SimpleStrictService {
      override def simpleGet(): ServerServiceCall[NotUsed, String] = ServerServiceCall { (reqHeader, _) =>
        Future {
          reqHeader.getHeader(VerboseHeaderPlayFilter.addedOnRequest) should be(Some("1"))
        }.recoverWith {
          case t => Future.failed(BadRequest(s"Assertion failed: ${t.getMessage}"))
        }.map { _ =>
          (ResponseHeader.Ok.withHeader("in-service", atomicInt.incrementAndGet().toString), hardcodedResponse)
        }
      }
    }

    val x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result] = {
      (action) => new VerboseHeaderPlayFilter(atomicInt, mat).apply(rh => action(rh).run())
    }

    runRequest(service)(x) {
      _ should be(
        mvc.Results.Ok(hardcodedResponse)
          .withHeaders(
            ("in-service", "2"),
            (VerboseHeaderPlayFilter.addedOnResponse, "3")
          )
      )
    }
  }

  it should "propagate headers altered by a Play Filter and a Lagom HeaderFilter down to the ServiceImpl (invoking Play Filter first). [String message]" in {
    // this test makes sure headers in request and response are added and they are added in the appropriate order.
    val atomicInt = new AtomicInteger(0)
    val hardcodedResponse = "a response"
    val service = new FilteredStrictService(atomicInt) {
      override def simpleGet(): ServerServiceCall[NotUsed, String] = ServerServiceCall {
        (reqHeader, _) =>
          Future {
            (
              reqHeader.getHeader(VerboseHeaderPlayFilter.addedOnRequest), reqHeader.getHeader(VerboseHeaderLagomFilter.addedOnRequest)
            ) should be(
                (Some("1"), Some("2"))
              )
            // When this assertion fails, the AssertionException is mapped to a BadRequest but the
            // exception serializer looses the exception message. Use the status code to locate the
            // cause of failure.
            // "1" and "2" are set on play filter and lagom filter respectively
          }.recoverWith {
            case t => Future.failed(BadRequest(s"Assertion failed: ${
              t.getMessage
            }"))
          }.map {
            _ =>
              // if both headers are present, OK is returned with a new header from the service.
              // the filters will add two more headers.
              (ResponseHeader.Ok.withHeader("in-service", atomicInt.incrementAndGet().toString), hardcodedResponse)
          }
      }
    }

    val x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result] = {
      (action) => new VerboseHeaderPlayFilter(atomicInt, mat).apply(rh => action(rh).run())
    }

    runRequest(service)(x) {
      _ should be(
        // when everything works as expected, the service receives 2 headers with values '1' and '2' and responds
        // with three headers '3', '4' and '5'. In case of failure, some headers may still be added on the way out
        // so make sure to check the status code on the response for more details on the cause of the error.
        mvc.Results.Ok(hardcodedResponse)
          .withHeaders(
            ("in-service", "3"), // when this is missing it means the ServiceImpl code failed == missing request headers
            (VerboseHeaderLagomFilter.addedOnResponse, "4"),
            (VerboseHeaderPlayFilter.addedOnResponse, "5")
          )
      )
    }
  }
  // ---------------------------------------------------------------------------------------------------

  private def runRequest[T](service: Service)(x: mvc.EssentialAction => mvc.RequestHeader => Future[mvc.Result])(block: mvc.Result => T): Future[T] = {
    val httpConfig = HttpConfiguration.createWithDefaults()
    val router = new ScaladslServiceRouter(service.descriptor, service, httpConfig)
    val req: mvc.Request[NotUsed] = new FakeRequest(method = "GET", path = PathProvider.PATH)
    val reqHeader: mvc.RequestHeader = req
    val handler = router.routes(reqHeader)
    val futureResult: Future[mvc.Result] = handler match {
      case action: mvc.EssentialAction => x(action)(reqHeader)
      case _                           => Future.failed(PayloadTooLarge("Not an EssentialAction."))
    }
    futureResult map block
  }
}

