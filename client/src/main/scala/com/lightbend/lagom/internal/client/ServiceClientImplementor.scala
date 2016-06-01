/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.lang.reflect.{ InvocationHandler, Method }
import java.net.{ URI, URLEncoder }
import java.util.function.BiFunction
import java.util.{ Optional, function }
import java.util.concurrent.CompletionStage
import javax.inject.{ Inject, Singleton }

import akka.stream.Materializer
import akka.stream.javadsl.{ Source => JSource }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.lightbend.lagom.internal.api.{ MethodServiceCallHolder, Path }
import akka.NotUsed
import com.lightbend.lagom.javadsl.api.Descriptor.{ Call, RestCallId }
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.NegotiatedSerializer
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.api.security.ServicePrincipal
import com.lightbend.lagom.javadsl.api.transport._
import com.lightbend.lagom.javadsl.api.{ Descriptor, ServiceCall, ServiceInfo, ServiceLocator }
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import org.pcollections.{ HashTreePMap, PSequence, TreePVector }
import play.api.Environment
import play.api.http.HeaderNames
import play.api.libs.streams.AkkaStreams
import play.api.libs.ws.{ InMemoryBody, WSClient }

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Implements a service client.
 */
@Singleton
class ServiceClientImplementor @Inject() (ws: WSClient, webSocketClient: WebSocketClient, serviceInfo: ServiceInfo,
                                          serviceLocator: ServiceLocator, environment: Environment)(implicit ec: ExecutionContext, mat: Materializer) {

  def implement[T](interface: Class[T], descriptor: Descriptor): T = {
    java.lang.reflect.Proxy.newProxyInstance(environment.classLoader, Array(interface), new ServiceClientInvocationHandler(descriptor)).asInstanceOf[T]
  }

  class ServiceClientInvocationHandler(descriptor: Descriptor) extends InvocationHandler {
    private val methods: Map[Method, ServiceCallInvocationHandler[Any, Any]] = descriptor.calls().asScala.map { call =>
      call.serviceCallHolder() match {
        case holder: MethodServiceCallHolder =>
          holder.method -> new ServiceCallInvocationHandler[Any, Any](ws, webSocketClient, serviceInfo, serviceLocator,
            descriptor, call.asInstanceOf[Call[Any, Any]], holder)
      }
    }.toMap

    override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]): AnyRef = {
      methods.get(method) match {
        case Some(serviceCallInvocationHandler) => serviceCallInvocationHandler.invoke(args)
        case None                               => throw new IllegalStateException("Method " + method + " is not described by the service client descriptor")
      }
    }
  }
}

private class ServiceCallInvocationHandler[Request, Response](ws: WSClient, webSocketClient: WebSocketClient,
                                                              serviceInfo: ServiceInfo, serviceLocator: ServiceLocator,
                                                              descriptor: Descriptor, endpoint: Call[Request, Response], holder: MethodServiceCallHolder)(implicit ec: ExecutionContext, mat: Materializer) {
  private val pathSpec = Path.fromCallId(endpoint.callId)

  def invoke(args: Seq[AnyRef]): ServiceCall[Request, Response] = {
    val (path, queryParams) = pathSpec.format(holder.invoke(args))

    new ClientServiceCall[Request, Response, Response](new ClientServiceCallInvoker[Request, Response](ws, webSocketClient,
      serviceInfo, serviceLocator, descriptor, endpoint, path, queryParams), identity, (_, msg) => msg)
  }
}

/**
 * The service call implementation. Delegates actual work to the invoker, while maintaining the handler function for
 * the request header and a transformer function for the response.
 */
private class ClientServiceCall[Request, ResponseMessage, ServiceCallResponse](
  invoker: ClientServiceCallInvoker[Request, ResponseMessage], requestHeaderHandler: RequestHeader => RequestHeader,
  responseHandler: (ResponseHeader, ResponseMessage) => ServiceCallResponse
)(implicit ec: ExecutionContext) extends ServiceCall[Request, ServiceCallResponse] {

  override def invoke(request: Request): CompletionStage[ServiceCallResponse] = {
    invoker.doInvoke(request, requestHeaderHandler).map(responseHandler.tupled).toJava
  }

  override def handleRequestHeader(handler: function.Function[RequestHeader, RequestHeader]): ServiceCall[Request, ServiceCallResponse] = {
    new ClientServiceCall(invoker, requestHeaderHandler.andThen(handler.apply), responseHandler)
  }

  override def handleResponseHeader[T](handler: BiFunction[ResponseHeader, ServiceCallResponse, T]): ServiceCall[Request, T] = {
    new ClientServiceCall[Request, ResponseMessage, T](invoker, requestHeaderHandler,
      (header, message) => handler.apply(header, responseHandler(header, message)))
  }

  /**
   * This is overridden in an attempt to try and provide better error reporting for when the request is not a unit type.
   */
  override def invoke(): CompletionStage[ServiceCallResponse] = {
    if (invoker.endpoint.requestSerializer() != MessageSerializers.NOT_USED) {
      throw new UnsupportedOperationException("Invocation without a request message may only be done when the request message is NotUsed. Use invoke(Id, Request) instead.")
    } else {
      invoke(NotUsed.asInstanceOf[Request])
    }
  }
}

private class ClientServiceCallInvoker[Request, Response](
  ws: WSClient, webSocketClient: WebSocketClient, serviceInfo: ServiceInfo, serviceLocator: ServiceLocator,
  descriptor: Descriptor, val endpoint: Call[Request, Response], path: String, queryParams: Map[String, Seq[String]]
)(implicit ec: ExecutionContext, mat: Materializer) {

  def doInvoke(request: Request, requestHeaderHandler: RequestHeader => RequestHeader): Future[(ResponseHeader, Response)] = {
    serviceLocator.doWithService(descriptor.name, endpoint, new java.util.function.Function[URI, CompletionStage[(ResponseHeader, Response)]] {
      override def apply(uri: URI) = {

        val queryString = if (queryParams.nonEmpty) {
          queryParams.flatMap {
            case (name, values) => values.map(value => URLEncoder.encode(name, "utf-8") + "=" + URLEncoder.encode(value, "utf-8"))
          }.mkString("?", "&", "")
        } else ""
        val url = s"$uri$path$queryString"

        val method = endpoint.callId match {
          case rest: RestCallId => rest.method
          case _ => if (endpoint.requestSerializer.isUsed) {
            com.lightbend.lagom.javadsl.api.transport.Method.POST
          } else {
            com.lightbend.lagom.javadsl.api.transport.Method.GET
          }
        }

        val requestHeader = requestHeaderHandler(new RequestHeader(method, URI.create(url), endpoint.requestSerializer.serializerForRequest.protocol,
          endpoint.responseSerializer.acceptResponseProtocols(),
          Optional.of(ServicePrincipal.forServiceNamed(serviceInfo.serviceName)), HashTreePMap.empty()))

        val result: Future[(ResponseHeader, Response)] = (endpoint.requestSerializer(), endpoint.responseSerializer()) match {
          case (requestSerializer: StrictMessageSerializer[Request], responseSerializer: StrictMessageSerializer[Response]) =>
            makeStrictCall(requestHeader, requestSerializer, responseSerializer, request)
          case (requestSerializer: StrictMessageSerializer[Request], responseSerializer: StreamedMessageSerializer[_]) =>
            makeStreamedResponseCall(requestHeader, requestSerializer, responseSerializer, request)
          case (requestSerializer: StreamedMessageSerializer[_], responseSerializer: StrictMessageSerializer[Response]) =>
            makeStreamedRequestCall(requestHeader, requestSerializer, responseSerializer, request)
          case (requestSerializer: StreamedMessageSerializer[_], responseSerializer: StreamedMessageSerializer[_]) =>
            makeStreamedCall(requestHeader, requestSerializer, responseSerializer, request)
        }

        result.toJava
      }
    }).toScala.map { responseOption =>
      if (responseOption.isPresent) {
        responseOption.get
      } else {
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

    val serializer = requestSerializer.serializerForRequest()

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

    val serializer = requestSerializer.asInstanceOf[StreamedMessageSerializer[Any]].serializerForRequest()
    val requestStream = serializer.serialize(request.asInstanceOf[JSource[Any, _]])

    val transportRequestHeader = descriptor.headerFilter.transformClientRequest(requestHeader)

    for {
      (transportResponseHeader, responseStream) <- doMakeStreamedCall(requestStream.asScala, serializer, transportRequestHeader)
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

    val serializer = requestSerializer.asInstanceOf[StreamedMessageSerializer[Any]].serializerForRequest()
    val requestStream = serializer.serialize(request.asInstanceOf[JSource[Any, _]])

    val transportRequestHeader = descriptor.headerFilter.transformClientRequest(requestHeader)

    doMakeStreamedCall(requestStream.asScala, serializer, transportRequestHeader).map(
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
    responseHeader -> deserializer.deserialize(response.asJava).asInstanceOf[Response]
  }

  private def doMakeStreamedCall(requestStream: Source[ByteString, _], requestSerializer: NegotiatedSerializer[_, _],
                                 requestHeader: RequestHeader): Future[(ResponseHeader, Source[ByteString, _])] = {
    webSocketClient.connect(descriptor.exceptionSerializer, WebSocketVersion.V13, requestHeader,
      requestStream)
  }

  /**
   * A call that is strict in both directions.
   */
  private def makeStrictCall(requestHeader: RequestHeader, requestSerializer: StrictMessageSerializer[Request], responseSerializer: StrictMessageSerializer[Response],
                             request: Request): Future[(ResponseHeader, Response)] = {
    val serializer = requestSerializer.serializerForRequest()
    val body = serializer.serialize(request)

    val transportRequestHeader = descriptor.headerFilter.transformClientRequest(requestHeader)

    val requestHolder = ws.url(requestHeader.uri.toString)
      .withMethod(requestHeader.method.name)
      .withHeaders(transportRequestHeader.headers.asScala.toSeq.map {
        case (name, values) => name -> values.asScala.mkString(", ")
      }: _*)

    val requestWithBody = if (requestSerializer.isUsed) {
      val contentType = transportRequestHeader.protocol.toContentTypeHeader.get
      requestHolder.withBody(InMemoryBody(body))
        .withHeaders(HeaderNames.CONTENT_TYPE -> contentType)
    } else requestHolder

    val accept = transportRequestHeader.acceptedResponseProtocols.asScala.flatMap { accept =>
      accept.toContentTypeHeader.asScala
    }.mkString(", ")
    val acceptHeader = if (accept.nonEmpty) {
      Seq(HeaderNames.ACCEPT -> accept)
    } else {
      Nil
    }

    requestWithBody.withHeaders(acceptHeader: _*).execute().map { response =>

      // Create the message header
      val protocol = MessageProtocol.fromContentTypeHeader(response.header(HeaderNames.CONTENT_TYPE).asJava)
      val headers = response.allHeaders.foldLeft(HashTreePMap.empty[String, PSequence[String]]) {
        case (map, (key, values)) => map.plus(key, TreePVector.from(values.asJava))
      }
      val transportResponseHeader = new ResponseHeader(response.status, protocol, headers)
      val responseHeader = descriptor.headerFilter.transformClientResponse(transportResponseHeader, requestHeader)

      if (response.status >= 400 && response.status <= 599) {
        val errorCode = TransportErrorCode.fromHttp(response.status)
        val rawExceptionMessage = new RawExceptionMessage(errorCode, protocol, response.bodyAsBytes)
        throw descriptor.exceptionSerializer.deserialize(rawExceptionMessage)
      } else {
        val deserializer = responseSerializer.deserializer(responseHeader.protocol)
        responseHeader -> deserializer.deserialize(response.bodyAsBytes)
      }
    }
  }

}
