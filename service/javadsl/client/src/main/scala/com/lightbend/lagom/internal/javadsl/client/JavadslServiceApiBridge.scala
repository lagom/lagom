/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client

import java.net.URI
import java.security.Principal
import java.util.Locale
import java.util.concurrent.CompletionStage

import akka.stream.javadsl.{ Source => JSource }
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import com.lightbend.lagom.javadsl.api.{ deser, transport }
import com.lightbend.lagom.javadsl.api
import com.lightbend.lagom.javadsl.api.Descriptor.RestCallId
import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage
import com.lightbend.lagom.javadsl.api.security.ServicePrincipal
import com.lightbend.lagom.javadsl.api.transport.BadRequest
import org.pcollections.{ HashTreePMap, PSequence, TreePVector }

import scala.compat.java8.OptionConverters._
import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

trait JavadslServiceApiBridge extends LagomServiceApiBridge {
  override type AkkaStreamsSource[T, M] = JSource[T, M]
  override def akkaStreamsSourceAsScala[T, M](source: AkkaStreamsSource[T, M]): Source[T, M] = source.asScala
  override def toAkkaStreamsSource[T, M](source: Source[T, M]): AkkaStreamsSource[T, M] = source.asJava

  override type FutureType[_] = CompletionStage[_]

  override type MessageProtocol = transport.MessageProtocol
  override def messageProtocolIsUtf8(mp: MessageProtocol): Boolean = mp.isUtf8
  override def messageProtocolIsText(mp: MessageProtocol): Boolean = mp.isText
  override def messageProtocolContentType(mp: MessageProtocol): Option[String] = mp.contentType().asScala
  override def messageProtocolCharset(mp: MessageProtocol): Option[String] = mp.charset().asScala
  override def messageProtocolToContentTypeHeader(mp: MessageProtocol): Option[String] = mp.toContentTypeHeader.asScala
  override def messageProtocolFromContentTypeHeader(ct: Option[String]): MessageProtocol =
    transport.MessageProtocol.fromContentTypeHeader(ct.asJava)
  override def newMessageProtocol(ct: Option[String], cs: Option[String], v: Option[String]): MessageProtocol =
    new transport.MessageProtocol(ct.asJava, cs.asJava, v.asJava)

  override type MessageHeader = transport.MessageHeader
  override def messageHeaderProtocol(mh: MessageHeader): MessageProtocol = mh.protocol()
  override def messageHeaderHeaders(mh: MessageHeader): Map[String, immutable.Seq[(String, String)]] =
    mh.headers().asScala.toSeq.flatMap {
      case (key, values) => values.asScala.map(key -> _)
    }.groupBy(_._1.toLowerCase(Locale.ENGLISH)).map {
      case (key, values) => key -> values.to[immutable.Seq]
    }

  override type RequestHeader = transport.RequestHeader
  override def requestHeaderUri(rh: RequestHeader): URI = rh.uri()
  override def requestHeaderAcceptedResponseProtocols(rh: RequestHeader): immutable.Seq[MessageProtocol] =
    rh.acceptedResponseProtocols().asScala.to[immutable.Seq]
  override def requestHeaderMethod(rh: RequestHeader): String = rh.method().name()
  override def newRequestHeader(method: Method, uri: URI, requestProtocol: MessageProtocol,
                                acceptResponseProtocols: immutable.Seq[MessageProtocol], servicePrincipal: Option[Principal],
                                headers: Map[String, immutable.Seq[(String, String)]]): RequestHeader = new transport.RequestHeader(method, uri,
    requestProtocol, TreePVector.from(acceptResponseProtocols.asJavaCollection), servicePrincipal.asJava, HashTreePMap.from(headers.map {
    case (key, Nil) => key -> (TreePVector.empty(): PSequence[String])
    case (key, values) =>
      values.head._1 -> TreePVector.from(values.map(_._2).asJava)
  }.asJava))

  override type ResponseHeader = transport.ResponseHeader
  override def responseHeaderStatus(rh: ResponseHeader): Int = rh.status()
  override def responseHeaderWithProtocol(rh: ResponseHeader, mp: MessageProtocol): ResponseHeader = rh.withProtocol(mp)
  override def newResponseHeader(code: Int, mp: MessageProtocol, headers: Map[String, immutable.Seq[(String, String)]]): ResponseHeader =
    new transport.ResponseHeader(code, mp, HashTreePMap.from(headers.map {
      case (key, Nil) => key -> (TreePVector.empty(): PSequence[String])
      case (key, values) =>
        values.head._1 -> TreePVector.from(values.map(_._2).asJava)
    }.asJava))
  override def responseHeaderIsDefault(rh: ResponseHeader): Boolean = rh == transport.ResponseHeader.OK

  override type MessageSerializer[M, W] = deser.MessageSerializer[M, W]
  override def messageSerializerSerializerForRequest[M, W](ms: MessageSerializer[M, W]): NegotiatedSerializer[M, W] =
    ms.serializerForRequest()
  override def messageSerializerSerializerForResponse[M, W](ms: MessageSerializer[M, W], ap: immutable.Seq[MessageProtocol]): NegotiatedSerializer[M, W] =
    ms.serializerForResponse(ap.asJava)
  override def messageSerializerDeserializer[M, W](ms: MessageSerializer[M, W], mp: MessageProtocol): NegotiatedDeserializer[M, W] =
    ms.deserializer(mp)
  override def messageSerializerAcceptResponseProtocols(ms: MessageSerializer[_, _]): immutable.Seq[MessageProtocol] =
    ms.acceptResponseProtocols().asScala.to[immutable.Seq]
  override def messageSerializerIsStreamed(ms: MessageSerializer[_, _]): Boolean = ms.isStreamed
  override def messageSerializerIsUsed(ms: MessageSerializer[_, _]): Boolean = ms.isUsed

  override type NegotiatedSerializer[M, W] = deser.MessageSerializer.NegotiatedSerializer[M, W]
  override def negotiatedSerializerProtocol(ns: NegotiatedSerializer[_, _]): MessageProtocol = ns.protocol()
  override def negotiatedSerializerSerialize[M, W](ns: NegotiatedSerializer[M, W], m: M): W = ns.serialize(m)

  override type NegotiatedDeserializer[M, W] = deser.MessageSerializer.NegotiatedDeserializer[M, W]
  override def negotiatedDeserializerDeserialize[M, W](ns: NegotiatedDeserializer[M, W], w: W): M = ns.deserialize(w)

  override type ExceptionSerializer = deser.ExceptionSerializer
  override def exceptionSerializerDeserializeHttpException(es: ExceptionSerializer, code: Int, mp: MessageProtocol, bytes: ByteString): Throwable = {
    val errorCode = transport.TransportErrorCode.fromHttp(code)
    es.deserialize(new deser.RawExceptionMessage(errorCode, mp, bytes))
  }
  override def exceptionSerializerDeserializeWebSocketException(es: ExceptionSerializer, code: Int, mp: MessageProtocol, bytes: ByteString): Throwable = {
    val errorCode = transport.TransportErrorCode.fromWebSocket(code)
    es.deserialize(new deser.RawExceptionMessage(errorCode, mp, bytes))
  }
  override def exceptionSerializerSerialize(es: ExceptionSerializer, t: Throwable, accept: immutable.Seq[MessageProtocol]): RawExceptionMessage = {
    es.serialize(t, accept.asJavaCollection)
  }

  override type RawExceptionMessage = deser.RawExceptionMessage
  override def rawExceptionMessageErrorCode(rem: RawExceptionMessage): ErrorCode = rem.errorCode()
  override def rawExceptionMessageMessage(rem: RawExceptionMessage): ByteString = rem.message()
  override def rawExceptionMessageWebSocketCode(rem: RawExceptionMessage): Int = rem.errorCode().webSocket()
  override def rawExceptionMessageMessageAsText(rem: RawExceptionMessage): String = rem.messageAsText()
  override def rawExceptionMessageToResponseHeader(rem: RawExceptionMessage): ResponseHeader =
    new transport.ResponseHeader(rem.errorCode().http(), rem.protocol(), HashTreePMap.empty())
  override def newRawExceptionMessage(errorCode: ErrorCode, protocol: MessageProtocol, message: ByteString): RawExceptionMessage =
    new deser.RawExceptionMessage(errorCode, protocol, message)

  override type ErrorCode = transport.TransportErrorCode

  override type ServiceCall[Request, Response] = api.ServiceCall[Request, Response]
  override type Call[Request, Response] = api.Descriptor.Call[Request, Response]
  override def methodForCall(call: Call[_, _]): Method =
    call.callId match {
      case rest: RestCallId => rest.method
      case _ => if (call.requestSerializer.isUsed) {
        transport.Method.POST
      } else {
        transport.Method.GET
      }
    }
  override def callRequestSerializer[Request, W](call: Call[Request, _]): MessageSerializer[Request, W] =
    call.requestSerializer().asInstanceOf[MessageSerializer[Request, W]]
  override def callResponseSerializer[Response, W](call: Call[_, Response]): MessageSerializer[Response, W] =
    call.responseSerializer().asInstanceOf[MessageSerializer[Response, W]]

  override type Method = transport.Method
  override def methodName(m: Method): String = m.name()
  override def newMethod(name: String): Method = new transport.Method(name)

  override type CallId = api.Descriptor.CallId

  override type Descriptor = api.Descriptor
  override def descriptorHeaderFilter(d: Descriptor): HeaderFilter = d.headerFilter()
  override def descriptorName(d: Descriptor): String = d.name()
  override def descriptorExceptionSerializer(d: Descriptor): ExceptionSerializer = d.exceptionSerializer()

  // Exceptions
  override def newPayloadTooLarge(msg: String): Throwable = new transport.PayloadTooLarge(msg)
  override def newPolicyViolation(msg: String, detail: String): Throwable =
    new transport.TransportException(transport.TransportErrorCode.PolicyViolation, new ExceptionMessage(msg, detail))
  override def newTransportException(errorCode: ErrorCode, message: String): Exception =
    new transport.TransportException(errorCode, new ExceptionMessage(message, ""))

  override type HeaderFilter = transport.HeaderFilter
  override def headerFilterTransformClientRequest(hf: HeaderFilter, rh: RequestHeader): RequestHeader = hf.transformClientRequest(rh)
  override def headerFilterTransformClientResponse(hf: HeaderFilter, resp: ResponseHeader, req: RequestHeader): ResponseHeader = hf.transformClientResponse(resp, req)
  override def headerFilterTransformServerRequest(hf: HeaderFilter, rh: RequestHeader): RequestHeader = hf.transformServerRequest(rh)
  override def headerFilterTransformServerResponse(hf: HeaderFilter, resp: ResponseHeader, req: RequestHeader): ResponseHeader = hf.transformServerResponse(resp, req)

  override type ServiceLocator = api.ServiceLocator

  override def serviceLocatorDoWithService[T](serviceLocator: ServiceLocator, descriptor: Descriptor,
                                              call: Call[_, _], block: (URI) => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = serviceLocator.doWithService(
    descriptor.name(),
    call, new java.util.function.Function[URI, CompletionStage[T]] {
      override def apply(uri: URI): CompletionStage[T] = block(uri).toJava
    }
  ).toScala.map(_.asScala)

  override def newServicePrincipal(serviceName: String): Principal = ServicePrincipal.forServiceNamed(serviceName)
}
