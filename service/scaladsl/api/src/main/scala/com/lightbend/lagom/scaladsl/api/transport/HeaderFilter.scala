/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.transport

import com.lightbend.lagom.scaladsl.api.security.ServicePrincipal
import play.api.http.HeaderNames

import scala.collection.immutable

/**
 * Filter for headers.
 *
 * This is used to transform transport and protocol headers according to various strategies for protocol and version
 * negotiation, as well as authentication.
 */
trait HeaderFilter {
  /**
   * Transform the given client request.
   *
   * This will be invoked on all requests outgoing from the client.
   *
   * @param request The client request header.
   * @return The transformed client request header.
   */
  def transformClientRequest(request: RequestHeader): RequestHeader

  /**
   * Transform the given server request.
   *
   * This will be invoked on all requests incoming to the server.
   *
   * @param request The server request header.
   * @return The transformed server request header.
   */
  def transformServerRequest(request: RequestHeader): RequestHeader

  /**
   * Transform the given server response.
   *
   * This will be invoked on all responses outgoing from the server.
   *
   * @param response The server response.
   * @param request  The transformed server request. Useful for when the response transformation requires information
   *                 from the client.
   * @return The transformed server response header.
   */
  def transformServerResponse(response: ResponseHeader, request: RequestHeader): ResponseHeader

  /**
   * Transform the given client response.
   *
   * This will be invoked on all responses incoming to the client.
   *
   * @param response The client response.
   * @param request  The client request. Useful for when the response transformation requires information from the
   *                 client request.
   * @return The transformed client response header.
   */
  def transformClientResponse(response: ResponseHeader, request: RequestHeader): ResponseHeader
}

object HeaderFilter {
  /**
   * A noop header transformer, used to deconfigure specific transformers.
   */
  val NoHeaderFilter: HeaderFilter = new HeaderFilter() {
    override def transformClientRequest(request: RequestHeader) = request
    override def transformServerRequest(request: RequestHeader) = request
    override def transformServerResponse(response: ResponseHeader, request: RequestHeader) = response
    override def transformClientResponse(response: ResponseHeader, request: RequestHeader) = response
  }

  /**
   * Create a composite header filter from multiple header filters.
   *
   * The order that the filters are applied are forward for headers being sent over the wire, and in reverse for
   * headers that are received from the wire.
   *
   * @param filters The filters to create the composite filter from.
   * @return The composite filter.
   */
  def composite(filters: HeaderFilter*): HeaderFilter = new Composite(filters.to[immutable.Seq])

  /**
   * A composite header filter.
   *
   * The order that the filters are applied are forward for headers being sent over the wire, and in reverse for
   * headers that are received from the wire.
   */
  final class Composite(headerFilters: immutable.Seq[HeaderFilter]) extends HeaderFilter {
    private val filters: immutable.Seq[HeaderFilter] = headerFilters.flatMap {
      case composite: Composite => composite.filters
      case other                => immutable.Seq(other)
    }

    def transformClientRequest(request: RequestHeader): RequestHeader = {
      filters.foldLeft(request)((req, filter) => filter.transformClientRequest(req))
    }

    def transformServerRequest(request: RequestHeader): RequestHeader = {
      filters.foldRight(request)((filter, req) => filter.transformServerRequest(req))
    }

    def transformServerResponse(response: ResponseHeader, request: RequestHeader): ResponseHeader = {
      filters.foldLeft(response)((resp, filter) => filter.transformServerResponse(resp, request))
    }

    def transformClientResponse(response: ResponseHeader, request: RequestHeader): ResponseHeader = {
      filters.foldRight(response)((filter, resp) => filter.transformClientResponse(response, request))
    }
  }

}

/**
 * Transfers service principal information via the `User-Agent` header.
 *
 * If using this on a service that serves requests from the outside world, it would be a good idea to block the
 * `User-Agent` header in the web facing load balancer/proxy.
 */
//#user-agent-header-filter
object UserAgentHeaderFilter extends HeaderFilter {
  override def transformClientRequest(request: RequestHeader) = {
    request.principal match {
      case Some(principal: ServicePrincipal) =>
        request.withHeader(HeaderNames.USER_AGENT, principal.serviceName)
      case _ => request
    }
  }

  override def transformServerRequest(request: RequestHeader) = {
    request.getHeader(HeaderNames.USER_AGENT) match {
      case Some(userAgent) =>
        request.withPrincipal(ServicePrincipal.forServiceNamed(userAgent))
      case _ =>
        request
    }
  }

  override def transformServerResponse(
    response: ResponseHeader,
    request:  RequestHeader
  ) = response

  override def transformClientResponse(
    response: ResponseHeader,
    request:  RequestHeader
  ) = response
}
//#user-agent-header-filter
