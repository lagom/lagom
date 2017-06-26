/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import com.lightbend.lagom.internal.api.Execution
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{ RequestHeader, ResponseHeader }

import scala.concurrent.Future

/**
 * A server implementation of a service call.
 *
 * While the server implementation of the service doesn't have to make use of this type, what this type does is it
 * allows the supply and composition of request and response headers.  When working with and or composing server
 * service calls, it is almost never a good idea to call [[#invoke(Object)]], rather,
 * [[#invokeWithHeaders(RequestHeader, Object)]] should be called. Invocation of the former may result in
 * an [[UnsupportedOperationException]] being thrown.
 *
 * In some cases, where the underlying transport doesn't allow sending a header after the request message has been
 * received (eg WebSockets), the response header may be ignored. In these cases, Lagom will make a best effort attempt
 * at determining whether there was custom information in the response header, and if so, log a warning that it wasn't
 * set.
 */
trait ServerServiceCall[Request, Response] extends ServiceCall[Request, Response] {

  /**
   * Invoke the given action with the request and response headers.
   *
   * @param requestHeader The request header.
   * @param request       The request message.
   * @return A future of the response header and response message.
   */
  def invokeWithHeaders(requestHeader: RequestHeader, request: Request): Future[(ResponseHeader, Response)] =
    invoke(request).map(response => (ResponseHeader.Ok, response))(Execution.trampoline)

  override def handleResponseHeader[T](handler: (ResponseHeader, Response) => T): ServerServiceCall[Request, T] = {
    val self: ServerServiceCall[Request, Response] = this
    new ServerServiceCall[Request, T] {
      override def invokeWithHeaders(requestHeader: RequestHeader, request: Request): Future[(ResponseHeader, T)] =
        self.invokeWithHeaders(requestHeader, request).map {
          case (responseHeader, response) => responseHeader -> handler(responseHeader, response)
        }(Execution.trampoline)

      def invoke(request: Request): Future[T] = {
        // Typically, the transport will attach a response header handler after it attaches a request header
        // handler.  So this service call will be the one that it invokes, meaning this is method that it
        // will call, and self will be the service call returned by handleRequestHeader.
        self.invokeWithHeaders(RequestHeader.Default, request).map {
          case (responseHeader, response) => handler(responseHeader, response)
        }(Execution.trampoline)
      }
    }
  }

  override def handleRequestHeader(handler: RequestHeader => RequestHeader): ServerServiceCall[Request, Response] = {
    val self: ServerServiceCall[Request, Response] = this
    new ServerServiceCall[Request, Response] {
      override def invokeWithHeaders(requestHeader: RequestHeader, request: Request): Future[(ResponseHeader, Response)] = {
        // Typically, this will be invoked by the service call returned by handleResponseHeader, which will
        // appropriately handle the response header returned by invokeWithHeaders.  Self will typically be the
        // user supplied service call.
        self.invokeWithHeaders(handler(requestHeader), request)
      }

      def invoke(request: Request): Future[Response] = {
        val requestHeader: RequestHeader = handler.apply(RequestHeader.Default)
        self.invokeWithHeaders(requestHeader, request).map(_._2)(Execution.trampoline)
      }
    }
  }
}

object ServerServiceCall {

  /**
   * Factory for creating a ServerServiceCall.
   *
   * This exists as a convenience function for implementing service calls that need to be composed with other calls
   * that handle headers.
   */
  def apply[Request, Response](serviceCall: Request => Future[Response]): ServerServiceCall[Request, Response] = {
    new ServerServiceCall[Request, Response] {
      override def invoke(request: Request): Future[Response] = serviceCall(request)
    }
  }

  /**
   * A service call that can handle headers.
   *
   * This exists as a convenience function for implementing service calls that handle the request and response
   * headers.
   */
  def apply[Request, Response](serviceCall: (RequestHeader, Request) => Future[(ResponseHeader, Response)]): ServerServiceCall[Request, Response] = new ServerServiceCall[Request, Response] {
    override def invoke(request: Request): Future[Response] =
      throw new UnsupportedOperationException("ServerServiceCalls should be invoked by using the invokeWithHeaders method.")

    override def invokeWithHeaders(requestHeader: RequestHeader, request: Request): Future[(ResponseHeader, Response)] =
      serviceCall(requestHeader, request)
  }

  /**
   * Compose a server service call.
   *
   * This is useful for implementing service call composition.  For example:
   *
   * ```
   * def authenticated[Request, Response](
   *   authenticatedServiceCall: String => ServerServiceCall[Request, Response]
   * ): ServerServiceCall[Request, Response] = {
   *
   *   ServerServiceCall.compose { requestHeader =>
   *
   *     // Get the logged in user ID
   *     val userId = requestHeader.principal.getOrElse {
   *       throw new NotAuthenticated("Not authenticated")
   *     }.getName
   *
   *     // Pass the user id to the composed service call
   *     authenticatedServiceCall(userId)
   *   }
   * }
   * ```
   *
   * @param block The block that will do the composition.
   * @return A service call.
   */
  def compose[Request, Response](block: RequestHeader => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] = ServerServiceCall { (requestHeader, request) =>
    block(requestHeader).invokeWithHeaders(requestHeader, request)
  }

  /**
   * Compose a header service call asynchronously.
   *
   * This is useful for implementing service call composition.  For example:
   *
   * ```
   * def authenticated[Request, Response](
   *   authenticatedServiceCall: String => ServerServiceCall[Request, Response]
   * ): ServerServiceCall[Request, Response] = {
   *
   *   ServerServiceCall.composeAsync { requestHeader =>
   *
   *     // Get the logged in user ID
   *     val userId = requestHeader.principal.getOrElse {
   *       throw new NotAuthenticated("Not authenticated")
   *     }.getName
   *
   *     // Load the user from the user service
   *     val userFuture: Future[User] = userService.loadUser(userId)
   *
   *     // Pass the user to the composed service call
   *     userFuture.map(user => authenticatedServiceCall(user))
   *   }
   * }
   * ```
   *
   * @param block The block that will do the composition.
   * @return A service call.
   */
  def composeAsync[Request, Response](block: RequestHeader => Future[ServerServiceCall[Request, Response]]): ServerServiceCall[Request, Response] = {
    ServerServiceCall { (requestHeader, request) =>
      block(requestHeader).flatMap(_.invokeWithHeaders(requestHeader, request))(Execution.trampoline)
    }
  }
}
