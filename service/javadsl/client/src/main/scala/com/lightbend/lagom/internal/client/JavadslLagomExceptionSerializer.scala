/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import akka.util.ByteString
import com.lightbend.lagom.internal.api.transport.{ LagomExceptionSerializer, LagomMessageProtocol, LagomRawExceptionMessage }
import com.lightbend.lagom.javadsl.api.deser.{ ExceptionMessage, ExceptionSerializer, RawExceptionMessage }
import com.lightbend.lagom.javadsl.api.transport.{ PayloadTooLarge, PolicyViolation, TransportErrorCode, TransportException }

import scala.collection.immutable.Seq
import scala.collection.JavaConverters._

class JavadslLagomExceptionSerializer(exceptionSerializer: ExceptionSerializer) extends LagomExceptionSerializer {
  override def deserializeWebSocketException(code: Int, requestProtocol: LagomMessageProtocol, bytes: ByteString): Throwable = {
    val errorCode = TransportErrorCode.fromWebSocket(code)
    exceptionSerializer.deserialize(new RawExceptionMessage(errorCode, LagomTransportConverters.convertLagomMessageProtocol(requestProtocol), bytes))
  }

  override def deserializeHttpException(code: Int, responseProtocol: LagomMessageProtocol, bytes: ByteString): Throwable = {
    val errorCode = TransportErrorCode.fromHttp(code)
    exceptionSerializer.deserialize(new RawExceptionMessage(errorCode, LagomTransportConverters.convertLagomMessageProtocol(responseProtocol), bytes))
  }

  override def serialize(exception: Throwable, acceptedProtocols: Seq[LagomMessageProtocol]): LagomRawExceptionMessage = {
    val rawException = exceptionSerializer.serialize(exception, acceptedProtocols.map(LagomTransportConverters.convertLagomMessageProtocol).asJavaCollection)
    new LagomRawExceptionMessage {
      override def messageText: String = rawException.messageAsText()

      override def httpCode: Int = rawException.errorCode().http()

      override def webSocketCode: Int = rawException.errorCode().webSocket()
    }
  }

  override def payloadTooLarge(message: String): Exception = new PayloadTooLarge(message)

  override def policyViolation(message: String, detail: String): Exception =
    new TransportException(TransportErrorCode.PolicyViolation, new ExceptionMessage(message, detail))
}
