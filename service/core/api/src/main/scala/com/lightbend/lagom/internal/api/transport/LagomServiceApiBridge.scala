/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.transport

import java.net.URI
import java.security.Principal

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.higherKinds

// INTERNAL API
// This provides a bridge between Lagom's core transport APIs, and the javadsl/scaladsl APIs
// The basic approach is rather than wrapping and unwrapping objects all the time, we just use the objects as is,
// and define methods to interrogate them here. This shouldn't create as much garbage, and helps to keep the bridge
// itself quite anaemic, which is a good thing.
trait LagomServiceApiBridge {

  // All the types that get adapted
  type AkkaStreamsSource[T, M]
  def akkaStreamsSourceAsScala[T, M](source: AkkaStreamsSource[T, M]): Source[T, M]
  def toAkkaStreamsSource[T, M](source: Source[T, M]): AkkaStreamsSource[T, M]

  type FutureType[T]

  type MessageProtocol
  def messageProtocolIsUtf8(mp: MessageProtocol): Boolean
  def messageProtocolIsText(mp: MessageProtocol): Boolean
  def messageProtocolCharset(mp: MessageProtocol): Option[String]
  def messageProtocolContentType(mp: MessageProtocol): Option[String]
  def messageProtocolToContentTypeHeader(mp: MessageProtocol): Option[String]
  def messageProtocolFromContentTypeHeader(ct: Option[String]): MessageProtocol
  def newMessageProtocol(ct: Option[String], cs: Option[String], v: Option[String]): MessageProtocol

  type MessageHeader
  def messageHeaderProtocol(mh: MessageHeader): MessageProtocol
  def messageHeaderHeaders(mh: MessageHeader): Map[String, immutable.Seq[(String, String)]]

  type RequestHeader <: MessageHeader
  def requestHeaderMethod(rh: RequestHeader): String
  def requestHeaderUri(rh: RequestHeader): URI
  def requestHeaderAcceptedResponseProtocols(rh: RequestHeader): immutable.Seq[MessageProtocol]
  def newRequestHeader(method: Method, uri: URI, requestProtocol: MessageProtocol,
                       acceptResponseProtocols: immutable.Seq[MessageProtocol], servicePrincipal: Option[Principal],
                       headers: Map[String, immutable.Seq[(String, String)]]): RequestHeader

  type ResponseHeader <: MessageHeader
  def responseHeaderStatus(rh: ResponseHeader): Int
  def newResponseHeader(code: Int, mp: MessageProtocol, headers: Map[String, immutable.Seq[(String, String)]]): ResponseHeader
  def responseHeaderWithProtocol(rh: ResponseHeader, mp: MessageProtocol): ResponseHeader
  def responseHeaderIsDefault(rh: ResponseHeader): Boolean

  type MessageSerializer[M, W]
  def messageSerializerSerializerForRequest[M, W](ms: MessageSerializer[M, W]): NegotiatedSerializer[M, W]
  def messageSerializerSerializerForResponse[M, W](ms: MessageSerializer[M, W], ap: immutable.Seq[MessageProtocol]): NegotiatedSerializer[M, W]
  def messageSerializerDeserializer[M, W](ms: MessageSerializer[M, W], mp: MessageProtocol): NegotiatedDeserializer[M, W]
  def messageSerializerAcceptResponseProtocols(ms: MessageSerializer[_, _]): immutable.Seq[MessageProtocol]
  def messageSerializerIsStreamed(ms: MessageSerializer[_, _]): Boolean
  def messageSerializerIsUsed(ms: MessageSerializer[_, _]): Boolean

  type NegotiatedSerializer[M, W]
  def negotiatedSerializerProtocol(ns: NegotiatedSerializer[_, _]): MessageProtocol
  def negotiatedSerializerSerialize[M, W](ns: NegotiatedSerializer[M, W], m: M): W

  type NegotiatedDeserializer[M, W]
  def negotiatedDeserializerDeserialize[M, W](ns: NegotiatedDeserializer[M, W], w: W): M

  type ExceptionSerializer
  def exceptionSerializerDeserializeHttpException(es: ExceptionSerializer, code: Int, mp: MessageProtocol,
                                                  bytes: ByteString): Throwable
  def exceptionSerializerDeserializeWebSocketException(es: ExceptionSerializer, code: Int, mp: MessageProtocol,
                                                       bytes: ByteString): Throwable
  def exceptionSerializerSerialize(es: ExceptionSerializer, t: Throwable,
                                   accept: immutable.Seq[MessageProtocol]): RawExceptionMessage

  type RawExceptionMessage
  def rawExceptionMessageErrorCode(rem: RawExceptionMessage): ErrorCode
  def rawExceptionMessageMessage(rem: RawExceptionMessage): ByteString
  def rawExceptionMessageWebSocketCode(rem: RawExceptionMessage): Int
  def rawExceptionMessageMessageAsText(rem: RawExceptionMessage): String
  def rawExceptionMessageToResponseHeader(rem: RawExceptionMessage): ResponseHeader
  def newRawExceptionMessage(errorCode: ErrorCode, protocol: MessageProtocol, message: ByteString): RawExceptionMessage

  type ErrorCode

  type ServiceCall[Request, Response]
  type Call[Request, Response]
  def methodForCall(call: Call[_, _]): Method
  def callRequestSerializer[Request, W](call: Call[Request, _]): MessageSerializer[Request, W]
  def callResponseSerializer[Response, W](call: Call[_, Response]): MessageSerializer[Response, W]

  type Method
  def methodName(m: Method): String
  def newMethod(name: String): Method

  type CallId
  type Descriptor
  def descriptorHeaderFilter(d: Descriptor): HeaderFilter
  def descriptorName(d: Descriptor): String
  def descriptorExceptionSerializer(d: Descriptor): ExceptionSerializer

  type HeaderFilter
  def headerFilterTransformClientRequest(hf: HeaderFilter, rh: RequestHeader): RequestHeader
  def headerFilterTransformClientResponse(hf: HeaderFilter, resp: ResponseHeader, req: RequestHeader): ResponseHeader
  def headerFilterTransformServerRequest(hf: HeaderFilter, rh: RequestHeader): RequestHeader
  def headerFilterTransformServerResponse(hf: HeaderFilter, resp: ResponseHeader, req: RequestHeader): ResponseHeader

  type ServiceLocator
  def serviceLocatorDoWithService[T](serviceLocator: ServiceLocator, descriptor: Descriptor, call: Call[_, _],
                                     block: URI => Future[T])(implicit ec: ExecutionContext): Future[Option[T]]

  // Exceptions
  def newPayloadTooLarge(msg: String): Throwable
  def newPolicyViolation(msg: String, detail: String): Throwable
  def newTransportException(errorCode: ErrorCode, message: String): Exception

  def newServicePrincipal(serviceName: String): Principal
}
