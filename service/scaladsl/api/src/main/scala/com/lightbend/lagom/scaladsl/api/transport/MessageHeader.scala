/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.transport

import java.net.URI
import java.security.Principal
import java.util.Locale

import com.lightbend.lagom.scaladsl.api.transport.RequestHeader.RequestHeaderImpl

import scala.collection.immutable
import scala.collection.immutable.Seq

/**
 * A message header.
 */
sealed trait MessageHeader {

  /**
   * The protocol of the message.
   */
  val protocol: MessageProtocol

  /**
   * The header map for the message.
   */
  val headerMap: Map[String, immutable.Seq[(String, String)]]

  /**
   * Get all the headers for the message.
   */
  def headers: Iterable[(String, String)] = headerMap.values.flatten

  /**
   * Get the header with the given name.
   *
   * The lookup is case insensitive.
   *
   * @param name The name of the header.
   * @return The header value.
   */
  def getHeader(name: String): Option[String] = {
    headerMap.get(name.toLowerCase(Locale.ENGLISH)).flatMap(_.headOption).map(_._2)
  }

  /**
   * Get all the header values for the given header name.
   *
   * The lookup is case insensitive.
   *
   * @param name The name of the header.
   * @return The header values.
   */
  def getHeaders(name: String): immutable.Seq[String] = {
    headerMap.get(name.toLowerCase(Locale.ENGLISH)).fold(immutable.Seq.empty[String])(_.map(_._2))
  }

  /**
   * Return a copy of this message header with the given protocol.
   *
   * @param protocol The protocol to set.
   * @return A copy of the message header with the given protocol.
   */
  def withProtocol(protocol: MessageProtocol): MessageHeader

  /**
   * Return a copy of this message header with the headers replaced by the given headers.
   *
   * @param headers The headers.
   * @return A copy of the message header with the given headers.
   */
  def withHeaders(headers: immutable.Seq[(String, String)]): MessageHeader

  /**
   * Return a copy of this message header with the given header added to the map of headers.
   *
   * If the header already has a value, this value will replace it.
   *
   * @param name  The name of the header to add.
   * @param value The value of the header to add.
   * @return The new message header.
   */
  def withHeader(name: String, value: String): MessageHeader

  /**
   * Return a copy of this message header with the given header added to the map of headers.
   *
   * If the header already has a value, this value will be added to it.
   *
   * @param name  The name of the header to add.
   * @param value The value of the header to add.
   * @return The new message header.
   */
  def addHeader(name: String, value: String): MessageHeader

  /**
   * Return a copy of this message header with the given header removed from the map of headers.
   *
   * @param name  The name of the header to remove.
   * @return The new message header.
   */
  def removeHeader(name: String): MessageHeader
}

/**
 * A request header.
 *
 * This header may or may not be mapped down onto HTTP.  In order to remain agnostic to the underlying protocol,
 * information required by Lagom, such as protocol information, is extracted.  It is encouraged that the protocol
 * information always be used in preference to reading the information directly out of headers, since the headers may
 * not contain the necessary protocol information.
 *
 * The headers are however still provided, in case information needs to be extracted out of non standard headers.
 */
sealed trait RequestHeader extends MessageHeader {
  /**
   * The method used to make this request.
   */
  val method: Method

  /**
   * The URI for this request.
   */
  val uri: URI

  /**
   * The accepted response protocols for this request.
   */
  val acceptedResponseProtocols: immutable.Seq[MessageProtocol]

  /**
   * The principal for this request, if there is one.
   */
  val principal: Option[Principal]

  /**
   * Return a copy of this request header with the given method set.
   *
   * @param method The method to set.
   * @return A copy of this request header.
   */
  def withMethod(method: Method): RequestHeader

  /**
   * Return a copy of this request header with the given uri set.
   *
   * @param uri The uri to set.
   * @return A copy of this request header.
   */
  def withUri(uri: URI): RequestHeader

  /**
   * Return a copy of this request header with the given accepted response protocols set.
   *
   * @param acceptedResponseProtocols The accepted response protocols to set.
   * @return A copy of this request header.
   */
  def withAcceptedResponseProtocols(acceptedResponseProtocols: immutable.Seq[MessageProtocol]): RequestHeader

  /**
   * Return a copy of this request header with the principal set.
   *
   * @param principal The principal to set.
   * @return A copy of this request header.
   */
  def withPrincipal(principal: Principal): RequestHeader

  /**
   * Return a copy of this request header with the principal cleared.
   *
   * @return A copy of this request header.
   */
  def clearPrincipal: RequestHeader

  override def withProtocol(protocol: MessageProtocol): RequestHeader
  override def withHeaders(headers: immutable.Seq[(String, String)]): RequestHeader
  override def withHeader(name: String, value: String): RequestHeader
  override def addHeader(name: String, value: String): RequestHeader
  override def removeHeader(name: String): RequestHeader
}

object RequestHeader {

  def apply(
    method:                    Method,
    uri:                       URI,
    protocol:                  MessageProtocol,
    acceptedResponseProtocols: immutable.Seq[MessageProtocol],
    principal:                 Option[Principal],
    headers:                   immutable.Seq[(String, String)]
  ): RequestHeader = RequestHeaderImpl(method, uri, protocol, acceptedResponseProtocols, principal,
    headers.groupBy(_._1.toLowerCase(Locale.ENGLISH)))

  val Default = RequestHeader(Method.GET, URI.create("/"), MessageProtocol.empty, Nil, None, Nil)

  private[lagom] def apply(
    method:                    Method,
    uri:                       URI,
    protocol:                  MessageProtocol,
    acceptedResponseProtocols: immutable.Seq[MessageProtocol],
    principal:                 Option[Principal],
    headerMap:                 Map[String, immutable.Seq[(String, String)]]
  ): RequestHeader = RequestHeaderImpl(method, uri, protocol, acceptedResponseProtocols, principal, headerMap)

  private case class RequestHeaderImpl(
    method:                    Method,
    uri:                       URI,
    protocol:                  MessageProtocol,
    acceptedResponseProtocols: immutable.Seq[MessageProtocol],
    principal:                 Option[Principal],
    headerMap:                 Map[String, immutable.Seq[(String, String)]]
  ) extends RequestHeader {
    override def withMethod(method: Method) = copy(method = method)
    override def withUri(uri: URI) = copy(uri = uri)
    override def withAcceptedResponseProtocols(acceptedResponseProtocols: Seq[MessageProtocol]) =
      copy(acceptedResponseProtocols = acceptedResponseProtocols)
    override def withPrincipal(principal: Principal) = copy(principal = Some(principal))
    override def clearPrincipal = copy(principal = None)
    override def withProtocol(protocol: MessageProtocol) = copy(protocol = protocol)
    override def withHeaders(headers: immutable.Seq[(String, String)]) =
      copy(headerMap = headers.groupBy(_._1.toLowerCase(Locale.ENGLISH)))
    override def withHeader(name: String, value: String) =
      copy(headerMap = headerMap + (name.toLowerCase(Locale.ENGLISH) -> immutable.Seq(name -> value)))
    override def addHeader(name: String, value: String) = {
      val lcName = name.toLowerCase(Locale.ENGLISH)
      headerMap.get(lcName) match {
        case None         => copy(headerMap = headerMap + (lcName -> immutable.Seq(name -> value)))
        case Some(values) => copy(headerMap = headerMap + (lcName -> (values :+ (name -> value))))
      }
    }
    override def removeHeader(name: String) =
      copy(headerMap = headerMap - name.toLowerCase(Locale.ENGLISH))
  }
}

/**
 * This header may or may not be mapped down onto HTTP.  In order to remain agnostic to the underlying protocol,
 * information required by Lagom, such as protocol information, is extracted.  It is encouraged that the protocol
 * information always be used in preference to reading the information directly out of headers, since the headers may
 * not contain the necessary protocol information.
 *
 * The headers are however still provided, in case information needs to be extracted out of non standard headers.
 */
sealed trait ResponseHeader extends MessageHeader {

  /**
   * The status code of the response.
   */
  val status: Int

  /**
   * Return a copy of this response with the given status code.
   */
  def withStatus(status: Int): ResponseHeader

  override def withProtocol(protocol: MessageProtocol): ResponseHeader
  override def withHeaders(headers: immutable.Seq[(String, String)]): ResponseHeader
  override def withHeader(name: String, value: String): ResponseHeader
  override def addHeader(name: String, value: String): ResponseHeader
  override def removeHeader(name: String): ResponseHeader
}

object ResponseHeader {
  def apply(
    status:   Int,
    protocol: MessageProtocol,
    headers:  immutable.Seq[(String, String)]
  ): ResponseHeader = ResponseHeaderImpl(status, protocol, headers.groupBy(_._1.toLowerCase(Locale.ENGLISH)))

  private[lagom] def apply(
    status:    Int,
    protocol:  MessageProtocol,
    headerMap: Map[String, immutable.Seq[(String, String)]]
  ): ResponseHeader = ResponseHeaderImpl(status, protocol, headerMap)

  val Ok: ResponseHeader = ResponseHeaderImpl(200, MessageProtocol.empty, Map.empty)

  private case class ResponseHeaderImpl(
    status:    Int,
    protocol:  MessageProtocol,
    headerMap: Map[String, immutable.Seq[(String, String)]]
  ) extends ResponseHeader {
    override def withStatus(status: Int): ResponseHeader = copy(status = status)
    override def withProtocol(protocol: MessageProtocol): ResponseHeader = copy(protocol = protocol)
    override def withHeaders(headers: immutable.Seq[(String, String)]) =
      copy(headerMap = headers.groupBy(_._1.toLowerCase(Locale.ENGLISH)))
    override def withHeader(name: String, value: String) =
      copy(headerMap = headerMap + (name.toLowerCase(Locale.ENGLISH) -> immutable.Seq(name -> value)))
    override def addHeader(name: String, value: String) = {
      val lcName = name.toLowerCase(Locale.ENGLISH)
      headerMap.get(lcName) match {
        case None         => copy(headerMap = headerMap + (lcName -> immutable.Seq(name -> value)))
        case Some(values) => copy(headerMap = headerMap + (lcName -> (values :+ (name -> value))))
      }
    }
    override def removeHeader(name: String) =
      copy(headerMap = headerMap - name.toLowerCase(Locale.ENGLISH))
  }
}
