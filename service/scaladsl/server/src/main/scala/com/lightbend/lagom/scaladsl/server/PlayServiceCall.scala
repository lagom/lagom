/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import com.lightbend.lagom.scaladsl.api.ServiceCall
import play.api.mvc.EssentialAction

import scala.concurrent.Future

/**
 * A service call implementation that allows plugging directly into Play's request handling.
 */
trait PlayServiceCall[Request, Response] extends ServiceCall[Request, Response] {

  def invoke(request: Request): Future[Response] = throw new UnsupportedOperationException("Play service call must be invoked using Play specific methods")

  /**
   * Low level hook for implementing service calls directly in Play.
   *
   * This can only be used to hook into plain HTTP calls, it can't be used to hook into WebSocket calls.
   *
   * @param wrapCall A function that takes a service call, and converts it to an EssentialAction.  This action can
   *                 then be composed to modify any part of the request and or response, including request and
   *                 response headers and the request and response body.  This does not have to be invoked at all if
   *                 it's not desired, but may be useful to get the benefit of all the Lagom features such as
   *                 serialization and deserialization.
   * @return An EssentialAction to handle the call with.
   */
  def invoke(wrapCall: ServiceCall[Request, Response] => EssentialAction): EssentialAction
}

object PlayServiceCall {

  /**
   * Convenience function for creating Play service calls.
   *
   * ```
   * def myServiceCall = PlayServiceCall { wrapCall =>
   *   EssentialAction { requestHeader =>
   *     // Wrap call can be invoked to get the action that will handle the call. It doesn't have to be
   *     // invoked, a check could be done for example that would short circuit its invocation.
   *     val wrappedAction = wrapCall(ServiceCall { request =>
   *       println(s"Service call invoked with $request")
   *       createSomeResponse()
   *     }
   *
   *     // This will get the accumulator to consume the body. The accumulator wraps an Akka streams Sink.
   *     // Working with this gives you an opportunity to modify the incoming stream in whatever way you want,
   *     // before it's passed to the Lagom deserializer to deserialize it.
   *     accumulator: Accumulator[ByteString, Result] = wrappedAction(requestHeader)
   *
   *     accumulator.map { result =>
   *       // At this point, the service call we invoked earlier has been invoked, and we now have the Play
   *       // result that it has been converted to. We can modify this result as much as we want, including
   *       // the body it is associated with.
   *       result
   *     }
   *   }
   * }
   * ```
   */
  def apply[Request, Response](serviceCall: (ServiceCall[Request, Response] => EssentialAction) => EssentialAction): PlayServiceCall[Request, Response] = {
    new PlayServiceCall[Request, Response] {
      override def invoke(wrapCall: (ServiceCall[Request, Response]) => EssentialAction): EssentialAction = serviceCall(wrapCall)
    }
  }

}
