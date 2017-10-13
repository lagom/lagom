/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.api.transport.{ Forbidden, HeaderFilter, RequestHeader, ResponseHeader }
import play.api.mvc.{ Filter, Result, RequestHeader => PlayRequestHeader, ResponseHeader => PlayResponseHeader }

import scala.concurrent.{ ExecutionContext, Future }

// ------------------------------------------------------------------------------------------------------------
// This is a play filter that adds a header on the request and the adds a header on the response. Headers may only
// be added once so invoking this Filter twice breaks the test.
class VerboseHeaderPlayFilter(atomicInt: AtomicInteger, mt: Materializer)(implicit ctx: ExecutionContext) extends Filter {

  import VerboseHeaderPlayFilter._

  override implicit def mat: Materializer = mt

  override def apply(f: (PlayRequestHeader) => Future[Result])(rh: PlayRequestHeader): Future[Result] = {
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
    if (headers.get(key).isDefined) throw Forbidden(s"Header $key already exists.")
}

object VerboseHeaderPlayFilter {
  val addedOnRequest = "addedOnRequest-play"
  val addedOnResponse = "addedOnResponse-play"
}

// ------------------------------------------------------------------------------------------------------------
// This is a Lagom HeaderFilter that adds a header on the request and the adds a header on the response.
class VerboseHeaderLagomFilter(atomicInteger: AtomicInteger) extends HeaderFilter {
  override def transformServerRequest(request: RequestHeader): RequestHeader =
    request.addHeader(VerboseHeaderLagomFilter.addedOnRequest, atomicInteger.incrementAndGet().toString)

  override def transformServerResponse(response: ResponseHeader, request: RequestHeader): ResponseHeader =
    response.addHeader(VerboseHeaderLagomFilter.addedOnResponse, atomicInteger.incrementAndGet().toString)

  override def transformClientResponse(response: ResponseHeader, request: RequestHeader): ResponseHeader = ???
  override def transformClientRequest(request: RequestHeader): RequestHeader = ???
}

object VerboseHeaderLagomFilter {
  val addedOnRequest = "addedOnRequest-Lagom"
  val addedOnResponse = "addedOnResponse-Lagom"
}
