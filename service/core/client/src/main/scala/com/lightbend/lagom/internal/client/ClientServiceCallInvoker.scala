/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.net.{ URI, URLEncoder }
import java.util.Locale

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import play.api.http.HeaderNames
import play.api.libs.streams.AkkaStreams
import play.api.libs.ws.{ InMemoryBody, WSClient }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

private[lagom] abstract class ClientServiceCallInvoker[Request, Response](
  ws: WSClient, serviceName: String, path: String, queryParams: Map[String, Seq[String]]
)(implicit ec: ExecutionContext, mat: Materializer) extends LagomServiceApiBridge {

  val descriptor: Descriptor
  val serviceLocator: ServiceLocator
  val call: Call[Request, Response]

  def doInvoke(request: Request, requestHeaderHandler: RequestHeader => RequestHeader): Future[(ResponseHeader, Response)] = {
    serviceLocatorDoWithService(serviceLocator, descriptor, call, uri => {
      val queryString = if (queryParams.nonEmpty) {
        queryParams.flatMap {
          case (name, values) => values.map(value => URLEncoder.encode(name, "utf-8") + "=" + URLEncoder.encode(value, "utf-8"))
        }.mkString("?", "&", "")
      } else ""
      val url = s"$uri$path$queryString"

      val method = methodForCall(call)

      val requestSerializer = callRequestSerializer(call)
      val serializer = messageSerializerSerializerForRequest[Request, Nothing](requestSerializer)
      val responseSerializer = callResponseSerializer(call)

      val requestHeader = requestHeaderHandler(newRequestHeader(method, URI.create(url), negotiatedSerializerProtocol(serializer),
        messageSerializerAcceptResponseProtocols(responseSerializer), Option(newServicePrincipal(serviceName)), Map.empty))

      val requestSerializerStreamed = messageSerializerIsStreamed(requestSerializer)
      val responseSerializerStreamed = messageSerializerIsStreamed(responseSerializer)

      val result: Future[(ResponseHeader, Response)] = (requestSerializerStreamed, responseSerializerStreamed) match {
        case (false, false) =>
          makeStrictCall(requestHeader, requestSerializer.asInstanceOf[MessageSerializer[Request, ByteString]],
            responseSerializer.asInstanceOf[MessageSerializer[Response, ByteString]], request)
        case (false, true) =>
          makeStreamedResponseCall(requestHeader, requestSerializer.asInstanceOf[MessageSerializer[Request, ByteString]],
            responseSerializer.asInstanceOf[MessageSerializer[Response, AkkaStreamsSource[ByteString, NotUsed]]], request)
        case (true, false) =>
          makeStreamedRequestCall(
            requestHeader,
            requestSerializer.asInstanceOf[MessageSerializer[Request, AkkaStreamsSource[ByteString, NotUsed]]],
            responseSerializer.asInstanceOf[MessageSerializer[Response, ByteString]], request
          )
        case (true, true) =>
          makeStreamedCall(
            requestHeader,
            requestSerializer.asInstanceOf[MessageSerializer[Request, AkkaStreamsSource[ByteString, NotUsed]]],
            responseSerializer.asInstanceOf[MessageSerializer[Response, AkkaStreamsSource[ByteString, NotUsed]]], request
          )
      }

      result
    }).map {
      case Some(response) => response
      case None =>
        throw new IllegalStateException(s"Service ${descriptorName(descriptor)} was not found by service locator")
    }
  }

  /**
   * A call that has a strict request and a streamed response.
   *
   * Currently implemented using a WebSocket, and sending the request as the first and only message.
   */
  private def makeStreamedResponseCall(
    requestHeader:      RequestHeader,
    requestSerializer:  MessageSerializer[Request, ByteString],
    responseSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]], request: Request
  ): Future[(ResponseHeader, Response)] = {

    val serializer = messageSerializerSerializerForRequest[Request, ByteString](requestSerializer)

    val transportRequestHeader = headerFilterTransformClientRequest(descriptorHeaderFilter(descriptor), requestHeader)

    // We have a single source, followed by a maybe source (that is, a source that never produces any message, and
    // never terminates). The maybe source is necessary because we want the response stream to stay open.
    val requestAsStream = if (messageSerializerIsUsed(requestSerializer)) {
      Source.single(negotiatedSerializerSerialize(serializer, request)).concat(Source.maybe)
    } else {
      // If it's not used, don't send any message
      Source.maybe[ByteString].mapMaterializedValue(_ => NotUsed)
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
    requestHeader: RequestHeader, requestSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
    responseSerializer: MessageSerializer[Response, ByteString], request: Request
  ): Future[(ResponseHeader, Response)] = {

    val serializer = messageSerializerSerializerForRequest(requestSerializer.asInstanceOf[MessageSerializer[AkkaStreamsSource[Any, NotUsed], AkkaStreamsSource[ByteString, NotUsed]]])
    val requestStream = negotiatedSerializerSerialize(serializer, request.asInstanceOf[AkkaStreamsSource[Any, NotUsed]])

    val headerFilter = descriptorHeaderFilter(descriptor)
    val transportRequestHeader = headerFilterTransformClientRequest(headerFilter, requestHeader)

    for {
      (transportResponseHeader, responseStream) <- doMakeStreamedCall(akkaStreamsSourceAsScala(requestStream), serializer, transportRequestHeader)
      // We want to take the first element (if it exists), and then ignore all subsequent elements. Ignoring, rather
      // than cancelling the stream, is important, because this is a WebSocket connection, we want the upstream to
      // still remain open, but if we cancel the stream, the upstream will disconnect too.
      maybeResponse <- responseStream via AkkaStreams.ignoreAfterCancellation runWith Sink.headOption
    } yield {
      val bytes = maybeResponse.getOrElse(ByteString.empty)
      val responseHeader = headerFilterTransformClientResponse(headerFilter, transportResponseHeader, requestHeader)
      val deserializer = messageSerializerDeserializer(responseSerializer, messageHeaderProtocol(responseHeader))
      responseHeader -> negotiatedDeserializerDeserialize(deserializer, bytes)
    }
  }

  /**
   * A call that is streamed in both directions.
   */
  private def makeStreamedCall(
    requestHeader: RequestHeader, requestSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
    responseSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]], request: Request
  ): Future[(ResponseHeader, Response)] = {

    val serializer = messageSerializerSerializerForRequest(
      requestSerializer.asInstanceOf[MessageSerializer[AkkaStreamsSource[Any, NotUsed], AkkaStreamsSource[ByteString, NotUsed]]]
    )
    val requestStream = negotiatedSerializerSerialize(serializer, request.asInstanceOf[AkkaStreamsSource[Any, NotUsed]])

    val transportRequestHeader = headerFilterTransformClientRequest(descriptorHeaderFilter(descriptor), requestHeader)

    doMakeStreamedCall(akkaStreamsSourceAsScala(requestStream), serializer, transportRequestHeader).map(
      (deserializeResponseStream(responseSerializer, requestHeader) _).tupled
    )
  }

  private def deserializeResponseStream(
    responseSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
    requestHeader:      RequestHeader
  )(transportResponseHeader: ResponseHeader, response: Source[ByteString, NotUsed]): (ResponseHeader, Response) = {
    val responseHeader = headerFilterTransformClientResponse(descriptorHeaderFilter(descriptor), transportResponseHeader, requestHeader)

    val deserializer = messageSerializerDeserializer(
      responseSerializer.asInstanceOf[MessageSerializer[AkkaStreamsSource[Any, NotUsed], AkkaStreamsSource[ByteString, NotUsed]]],
      messageHeaderProtocol(responseHeader)
    )
    responseHeader -> negotiatedDeserializerDeserialize(deserializer, toAkkaStreamsSource(response)).asInstanceOf[Response]
  }

  protected def doMakeStreamedCall(requestStream: Source[ByteString, NotUsed], requestSerializer: NegotiatedSerializer[_, _],
                                   requestHeader: RequestHeader): Future[(ResponseHeader, Source[ByteString, NotUsed])]

  /**
   * A call that is strict in both directions.
   */
  private def makeStrictCall(requestHeader: RequestHeader, requestSerializer: MessageSerializer[Request, ByteString],
                             responseSerializer: MessageSerializer[Response, ByteString], request: Request): Future[(ResponseHeader, Response)] = {

    val headerFilter = descriptorHeaderFilter(descriptor)
    val transportRequestHeader = headerFilterTransformClientRequest(headerFilter, requestHeader)
    val contentTypeHeader =
      messageProtocolToContentTypeHeader(messageHeaderProtocol(transportRequestHeader)).toSeq.map(HeaderNames.CONTENT_TYPE -> _)

    val requestHolder = ws.url(requestHeaderUri(requestHeader).toString)
      .withHttpHeaders(contentTypeHeader: _*)
      .withMethod(requestHeaderMethod(requestHeader))

    val requestWithBody =
      if (messageSerializerIsUsed(requestSerializer)) {
        val serializer = messageSerializerSerializerForRequest(requestSerializer)
        val body = negotiatedSerializerSerialize(serializer, request)

        requestHolder.withBody(InMemoryBody(body))
      } else requestHolder

    val requestHeaders = messageHeaderHeaders(transportRequestHeader).toSeq.collect {
      case (_, values) if values.nonEmpty => values.head._1 -> values.map(_._2).mkString(", ")
    }

    val acceptHeader = {
      val accept = requestHeaderAcceptedResponseProtocols(transportRequestHeader).flatMap { accept =>
        messageProtocolToContentTypeHeader(accept)
      }.mkString(", ")
      if (accept.nonEmpty) Seq(HeaderNames.ACCEPT -> accept)
      else Nil
    }

    requestWithBody.withHttpHeaders(requestHeaders ++ acceptHeader: _*).execute().map { response =>

      // Create the message header
      val protocol = messageProtocolFromContentTypeHeader(response.header(HeaderNames.CONTENT_TYPE))
      val headers = response.headers.map {
        case (key, values) => key.toLowerCase(Locale.ENGLISH) -> values.map(key -> _).to[immutable.Seq]
      }
      val transportResponseHeader = newResponseHeader(response.status, protocol, headers)
      val responseHeader = headerFilterTransformClientResponse(headerFilter, transportResponseHeader, requestHeader)

      if (response.status >= 400 && response.status <= 599) {
        throw exceptionSerializerDeserializeHttpException(
          descriptorExceptionSerializer(descriptor),
          response.status, protocol, response.bodyAsBytes
        )
      } else {
        val deserializer = messageSerializerDeserializer(responseSerializer, messageHeaderProtocol(responseHeader))
        responseHeader -> negotiatedDeserializerDeserialize(deserializer, response.bodyAsBytes)
      }
    }
  }

}
