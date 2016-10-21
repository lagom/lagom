/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import java.net.{ URI, URLEncoder }
import java.util.Locale
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.lightbend.lagom.internal.client.WebSocketClient
import com.lightbend.lagom.scaladsl.api.{ Descriptor, ServiceCall, ServiceInfo, ServiceLocator }
import com.lightbend.lagom.scaladsl.api.Descriptor.{ Call, RestCallId }
import com.lightbend.lagom.scaladsl.api.deser.{ MessageSerializer, RawExceptionMessage, StreamedMessageSerializer, StrictMessageSerializer }
import com.lightbend.lagom.scaladsl.api.security.ServicePrincipal
import com.lightbend.lagom.scaladsl.api.transport.{ MessageProtocol, RequestHeader, ResponseHeader, TransportErrorCode }
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import play.api.http.HeaderNames
import play.api.libs.streams.AkkaStreams
import play.api.libs.ws.{ InMemoryBody, WSClient }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

/**
 * The service call implementation. Delegates actual work to the invoker, while maintaining the handler function for
 * the request header and a transformer function for the response.
 */
private class ScaladslClientServiceCall[Request, ResponseMessage, ServiceCallResponse](
  invoker: ScaladslClientServiceCallInvoker[Request, ResponseMessage], requestHeaderHandler: RequestHeader => RequestHeader,
  responseHandler: (ResponseHeader, ResponseMessage) => ServiceCallResponse
)(implicit ec: ExecutionContext) extends ServiceCall[Request, ServiceCallResponse] {

  override def invoke(request: Request): Future[ServiceCallResponse] = {
    invoker.doInvoke(request, requestHeaderHandler).map(responseHandler.tupled)
  }

  override def handleRequestHeader(handler: RequestHeader => RequestHeader): ServiceCall[Request, ServiceCallResponse] = {
    new ScaladslClientServiceCall(invoker, requestHeaderHandler.andThen(handler), responseHandler)
  }

  override def handleResponseHeader[T](handler: (ResponseHeader, ServiceCallResponse) => T): ServiceCall[Request, T] = {
    new ScaladslClientServiceCall[Request, ResponseMessage, T](invoker, requestHeaderHandler,
      (header, message) => handler.apply(header, responseHandler(header, message)))
  }
}

private class ScaladslClientServiceCallInvoker[Request, Response](
  ws: WSClient, webSocketClient: WebSocketClient, serviceInfo: ServiceInfo, serviceLocator: ServiceLocator,
  descriptor: Descriptor, val endpoint: Call[Request, Response], path: String, queryParams: Map[String, Seq[String]]
)(implicit ec: ExecutionContext, mat: Materializer) {

  def doInvoke(request: Request, requestHeaderHandler: RequestHeader => RequestHeader): Future[(ResponseHeader, Response)] = {
    serviceLocator.doWithService(descriptor.name, endpoint) { uri =>
      val queryString = if (queryParams.nonEmpty) {
        queryParams.flatMap {
          case (name, values) => values.map(value => URLEncoder.encode(name, "utf-8") + "=" + URLEncoder.encode(value, "utf-8"))
        }.mkString("?", "&", "")
      } else ""
      val url = s"$uri$path$queryString"

      val method = endpoint.callId match {
        case rest: RestCallId => rest.method
        case _ => if (endpoint.requestSerializer.isUsed) {
          com.lightbend.lagom.scaladsl.api.transport.Method.POST
        } else {
          com.lightbend.lagom.scaladsl.api.transport.Method.GET
        }
      }

      val requestHeader = requestHeaderHandler(RequestHeader(method, URI.create(url), endpoint.requestSerializer.serializerForRequest.protocol,
        endpoint.responseSerializer.acceptResponseProtocols,
        Some(ServicePrincipal.forServiceNamed(serviceInfo.serviceName)), Nil))

      val result: Future[(ResponseHeader, Response)] = (endpoint.requestSerializer, endpoint.responseSerializer) match {
        case (requestSerializer: StrictMessageSerializer[Request], responseSerializer: StrictMessageSerializer[Response]) =>
          makeStrictCall(requestHeader, requestSerializer, responseSerializer, request)
        case (requestSerializer: StrictMessageSerializer[Request], responseSerializer: StreamedMessageSerializer[_]) =>
          makeStreamedResponseCall(requestHeader, requestSerializer, responseSerializer, request)
        case (requestSerializer: StreamedMessageSerializer[_], responseSerializer: StrictMessageSerializer[Response]) =>
          makeStreamedRequestCall(requestHeader, requestSerializer, responseSerializer, request)
        case (requestSerializer: StreamedMessageSerializer[_], responseSerializer: StreamedMessageSerializer[_]) =>
          makeStreamedCall(requestHeader, requestSerializer, responseSerializer, request)
      }

      result
    }.map { responseOption =>
      responseOption.getOrElse {
        throw new IllegalStateException(s"Service ${descriptor.name} was not found by service locator")
      }
    }
  }

  /**
   * A call that has a strict request and a streamed response.
   *
   * Currently implemented using a WebSocket, and sending the request as the first and only message.
   */
  private def makeStreamedResponseCall(
    requestHeader:      RequestHeader,
    requestSerializer:  StrictMessageSerializer[Request],
    responseSerializer: StreamedMessageSerializer[_], request: Request
  ): Future[(ResponseHeader, Response)] = {

    val serializer = requestSerializer.serializerForRequest

    val transportRequestHeader = descriptor.headerFilter.transformClientRequest(requestHeader)

    // We have a single source, followed by a maybe source (that is, a source that never produces any message, and
    // never terminates). The maybe source is necessary because we want the response stream to stay open.
    val requestAsStream = if (requestSerializer.isUsed) {
      Source.single(serializer.serialize(request)).concat(Source.maybe)
    } else {
      // If it's not used, don't send any message
      Source.maybe
    }
    doMakeStreamedCall(requestAsStream, serializer, transportRequestHeader).map(
      (deserializeResponseStream(responseSerializer, requestHeader) _).tupled
    )
  }

  /**
   * A call that has a streamed request and a strict response.
   *
   * Currently implemented using a WebSocket, that converts the first message received to the strict message. If no
   * message is received, it assumes the response is an empty message.
   */
  private def makeStreamedRequestCall(
    requestHeader: RequestHeader, requestSerializer: StreamedMessageSerializer[_],
    responseSerializer: StrictMessageSerializer[Response], request: Request
  ): Future[(ResponseHeader, Response)] = {

    val serializer = requestSerializer.asInstanceOf[StreamedMessageSerializer[Any]].serializerForRequest
    val requestStream = serializer.serialize(request.asInstanceOf[Source[Any, _]])

    val transportRequestHeader = descriptor.headerFilter.transformClientRequest(requestHeader)

    for {
      (transportResponseHeader, responseStream) <- doMakeStreamedCall(requestStream, serializer, transportRequestHeader)
      // We want to take the first element (if it exists), and then ignore all subsequent elements. Ignoring, rather
      // than cancelling the stream, is important, because this is a WebSocket connection, we want the upstream to
      // still remain open, but if we cancel the stream, the upstream will disconnect too.
      maybeResponse <- responseStream via AkkaStreams.ignoreAfterCancellation runWith Sink.headOption
    } yield {
      val bytes = maybeResponse.getOrElse(ByteString.empty)
      val responseHeader = descriptor.headerFilter.transformClientResponse(transportResponseHeader, requestHeader)
      val deserializer = responseSerializer.deserializer(responseHeader.protocol)
      responseHeader -> deserializer.deserialize(bytes)
    }
  }

  /**
   * A call that is streamed in both directions.
   */
  private def makeStreamedCall(
    requestHeader: RequestHeader, requestSerializer: StreamedMessageSerializer[_],
    responseSerializer: StreamedMessageSerializer[_], request: Request
  ): Future[(ResponseHeader, Response)] = {

    val serializer = requestSerializer.asInstanceOf[StreamedMessageSerializer[Any]].serializerForRequest
    val requestStream = serializer.serialize(request.asInstanceOf[Source[Any, _]])

    val transportRequestHeader = descriptor.headerFilter.transformClientRequest(requestHeader)

    doMakeStreamedCall(requestStream, serializer, transportRequestHeader).map(
      (deserializeResponseStream(responseSerializer, requestHeader) _).tupled
    )
  }

  private def deserializeResponseStream(
    responseSerializer: StreamedMessageSerializer[_],
    requestHeader:      RequestHeader
  )(transportResponseHeader: ResponseHeader, response: Source[ByteString, _]): (ResponseHeader, Response) = {
    val responseHeader = descriptor.headerFilter.transformClientResponse(transportResponseHeader, requestHeader)

    val deserializer = responseSerializer.asInstanceOf[StreamedMessageSerializer[Any]]
      .deserializer(responseHeader.protocol)
    responseHeader -> deserializer.deserialize(response).asInstanceOf[Response]
  }

  private def doMakeStreamedCall(requestStream: Source[ByteString, _], requestSerializer: MessageSerializer.NegotiatedSerializer[_, _],
                                 requestHeader: RequestHeader): Future[(ResponseHeader, Source[ByteString, _])] = {
    webSocketClient.connect(new ScaladslLagomExceptionSerializer(descriptor.exceptionSerializer), WebSocketVersion.V13,
      LagomTransportConverters.convertRequestHeader(requestHeader), requestStream).map {
        case (responseHeader, source) => LagomTransportConverters.convertLagomResponseHeader(responseHeader) -> source
      }
  }

  /**
   * A call that is strict in both directions.
   */
  private def makeStrictCall(requestHeader: RequestHeader, requestSerializer: StrictMessageSerializer[Request],
                             responseSerializer: StrictMessageSerializer[Response],
                             request:            Request): Future[(ResponseHeader, Response)] = {
    val requestHolder = ws.url(requestHeader.uri.toString)
      .withMethod(requestHeader.method.name)

    val requestWithBody =
      if (requestSerializer.isUsed) {
        val serializer = requestSerializer.serializerForRequest
        val body = serializer.serialize(request)

        requestHolder.withBody(InMemoryBody(body))
      } else requestHolder

    val transportRequestHeader = descriptor.headerFilter.transformClientRequest(requestHeader)

    val requestHeaders = transportRequestHeader.headers.toSeq.map {
      case (name, values) => name -> values.mkString(", ")
    }

    val contentTypeHeader =
      transportRequestHeader.protocol.toContentTypeHeader.toSeq.map(HeaderNames.CONTENT_TYPE -> _)

    val acceptHeader = {
      val accept = transportRequestHeader.acceptedResponseProtocols.flatMap { accept =>
        accept.toContentTypeHeader
      }.mkString(", ")
      if (accept.nonEmpty) Seq(HeaderNames.ACCEPT -> accept)
      else Nil
    }

    requestWithBody.withHeaders(requestHeaders ++ contentTypeHeader ++ acceptHeader: _*).execute().map { response =>

      // Create the message header
      val protocol = MessageProtocol.fromContentTypeHeader(response.header(HeaderNames.CONTENT_TYPE))
      val headers = response.allHeaders.map {
        case (key, values) => key.toLowerCase(Locale.ENGLISH) -> values.map(v => key -> v).to[immutable.Seq]
      }
      val transportResponseHeader = ResponseHeader(response.status, protocol, headers)
      val responseHeader = descriptor.headerFilter.transformClientResponse(transportResponseHeader, requestHeader)

      if (response.status >= 400 && response.status <= 599) {
        val errorCode = TransportErrorCode.fromHttp(response.status)
        val rawExceptionMessage = RawExceptionMessage(errorCode, protocol, response.bodyAsBytes)
        throw descriptor.exceptionSerializer.deserialize(rawExceptionMessage)
      } else {
        val deserializer = responseSerializer.deserializer(responseHeader.protocol)
        responseHeader -> deserializer.deserialize(response.bodyAsBytes)
      }
    }
  }

}
