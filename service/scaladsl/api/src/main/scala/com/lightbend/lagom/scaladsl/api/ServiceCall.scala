/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.{ RequestHeader, ResponseHeader }
import play.api.libs.iteratee.Execution

import scala.concurrent.Future

/**
 * A service call for an entity.
 *
 * A service call has a request and a response entity. Either entity may be NotUsed, if there is no entity associated
 * with the call. They may also be an Akka streams Source, in situations where the endpoint serves a stream. In all
 * other cases, the entities will be considered "strict" entities, that is, they will be parsed into memory, eg,
 * using json.
 */
trait ServiceCall[Request, Response] {
  /**
   * Invoke the service call.
   *
   * @param request The request entity.
   * @return A future of the response entity.
   */
  def invoke(request: Request): Future[Response]

  /**
   * Invoke the service call with unit id argument and a unit request message.
   *
   * This should only be used when the request message is NotUsed.
   *
   * @return A future of the response entity.
   */
  def invoke()(implicit evidence: Request =:= NotUsed): Future[Response] =
    this.asInstanceOf[ServiceCall[NotUsed, Response]].invoke(NotUsed)

  /**
   * Make any modifications necessary to the request header.
   *
   * For client service calls, this gives clients an opportunity to add custom headers and/or modify the request in
   * some way before it is made.  The passed in handler is applied before the header transformers
   * configured for the descriptor/endpoint are applied.
   *
   * For server implementations of service calls, this will be invoked by the server in order to supply the request
   * header.  A new service call can then be returned that uses the header.  The header passed in to the handler by
   * the service call can be anything, it will be ignored - [[RequestHeader#DEFAULT]] exists for this
   * purpose.  Generally, server implementations should not implement this method directly, rather, they should use
   * `ServerServiceCall`, which provides an appropriate implementation.
   *
   * @param handler A function that takes in the request header representing the request, and transforms it.
   * @return A service call that will use the given handler.
   */
  def handleRequestHeader(handler: RequestHeader => RequestHeader): ServiceCall[Request, Response] = {
    // Default implementation. For client service calls, this is overridden by the implementation to do something
    // with the handler.
    this
  }

  /**
   * Transform the response using the given function that takes the response header and the response.
   *
   * For client service calls, this gives clients an opportunity to inspect the response headers and status code.
   * The passed in handler is applied after the header transformers configured for the descriptor/endpoint are
   * applied.
   *
   * For server implementations of service calls, this will be invoked by the server in order to give the service
   * call an opportunity to supply the response header when it supplies the response, but only if the underlying
   * transport supports sending a response header.  Generally, server implementations should not implement this
   * method directly, rather, they should use <tt>ServerServiceCall</tt>, which provides an appropriate
   * implementation.
   *
   * @param handler The handler.
   * @return A service call that uses the given handler.
   */
  def handleResponseHeader[T](handler: (ResponseHeader, Response) => T): ServiceCall[Request, T] = {
    // Default implementation. For client service calls, this is overridden by the implementation to do something
    // with the handler.
    ServiceCall { request =>
      invoke(request).map(response => handler(ResponseHeader.Ok, response))(Execution.trampoline)
    }
  }

  /**
   * Allow handling of the response header.
   *
   * This converts the service call to one that returns both the response header and the response message.
   *
   * This is simply a convenience method for invoking <code>handleResponseHeader((_, _)</code>.
   *
   * @return The a service call that returns the response header and the response message.
   */
  def withResponseHeader: ServiceCall[Request, (ResponseHeader, Response)] =
    handleResponseHeader((_, _))
}

object ServiceCall {
  /**
   * Create a service call from a function to handle it.
   */
  def apply[Request, Response](call: Request => Future[Response]): ServiceCall[Request, Response] =
    new ServiceCall[Request, Response] {
      override def invoke(request: Request): Future[Response] = call.apply(request)
    }
}
