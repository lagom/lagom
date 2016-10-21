/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import com.lightbend.lagom.internal.api.transport.{ LagomMessageProtocol, LagomRequestHeader, LagomResponseHeader }
import com.lightbend.lagom.scaladsl.api.transport.{ MessageProtocol, RequestHeader, ResponseHeader }

object LagomTransportConverters {
  def convertLagomMessageProtocol(lagomMessageProtocol: LagomMessageProtocol): MessageProtocol =
    MessageProtocol(lagomMessageProtocol.contentType, lagomMessageProtocol.charset, lagomMessageProtocol.version)

  def convertMessageProtocol(messageProtocol: MessageProtocol): LagomMessageProtocol = {
    new LagomMessageProtocol(messageProtocol.contentType, messageProtocol.charset, messageProtocol.version)
  }

  def convertRequestHeader(requestHeader: RequestHeader): LagomRequestHeader = {
    new LagomRequestHeader(requestHeader.method.name, requestHeader.uri,
      convertMessageProtocol(requestHeader.protocol),
      requestHeader.acceptedResponseProtocols.map(convertMessageProtocol),
      requestHeader.principal, requestHeader.headerMap)
  }

  def convertLagomResponseHeader(lagomResponseHeader: LagomResponseHeader): ResponseHeader = {
    ResponseHeader(lagomResponseHeader.status, convertLagomMessageProtocol(lagomResponseHeader.protocol),
      lagomResponseHeader.headers)
  }

}
