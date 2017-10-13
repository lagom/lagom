/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import java.net.URI
import java.util.{ Base64, Locale }
import java.util.concurrent.CompletionException

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.util.ByteString
import com.lightbend.lagom.internal.api.Path
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import play.api.Logger
import play.api.http.HttpEntity.Strict
import play.api.http.websocket.{ BinaryMessage, CloseMessage, Message, TextMessage }
import play.api.http.{ HeaderNames, HttpConfiguration }
import play.api.libs.streams.{ Accumulator, AkkaStreams }
import play.api.mvc.{ BodyParser, BodyParsers, EssentialAction, Result, Results, WebSocket, RequestHeader => PlayRequestHeader }
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try
import scala.util.control.NonFatal

object ServiceRouter {
  /** RFC 6455 Section 5.5 - maximum control frame size is 125 bytes */
  val WebSocketControlFrameMaxLength = 125
}

private[lagom] abstract class ServiceRouter(httpConfiguration: HttpConfiguration)(implicit ec: ExecutionContext, mat: Materializer)
  extends SimpleRouter with LagomServiceApiBridge {

  protected val descriptor: Descriptor
  protected val serviceRoutes: Seq[ServiceRoute]

  import ServiceRouter._

  protected trait ServiceRoute {
    val call: Call[Any, Any]
    val path: Path
    val method: Method
    val isWebSocket: Boolean

    def createServiceCall(params: Seq[Seq[String]]): ServiceCall[Any, Any]
  }

  /**
   * The routes partial function.
   */
  override val routes: Routes = Function.unlift { request =>
    serviceRoutes.collectFirst(Function.unlift { route =>
      // We match by method, but since we ignore the method if it's a WebSocket (because WebSockets require that GET
      // is used) we also match if it's a WebSocket request and this can be handled as a WebSocket.
      if (methodName(route.method) == request.method || (request.method == "GET" && route.isWebSocket)) {

        val path = URI.create(request.uri).getRawPath
        val queryString = request.queryString
        route.path.extract(path, queryString).map { (params: Seq[Seq[String]]) =>
          val serviceCall = route.createServiceCall(params)

          // These casts are necessary due to an apparent scalac bug
          val requestSerializer = callRequestSerializer(route.call)
          val responseSerializer = callResponseSerializer(route.call)

          // If both request and response are strict, handle it using an action, otherwise handle it using a websocket
          if (messageSerializerIsStreamed(requestSerializer) || messageSerializerIsStreamed(responseSerializer)) {
            websocket(
              route.call.asInstanceOf[Call[Any, Any]],
              descriptor,
              serviceCall,
              requestSerializer,
              responseSerializer
            )
          } else {
            action(
              route.call.asInstanceOf[Call[Any, Any]],
              descriptor,
              serviceCall,
              requestSerializer.asInstanceOf[MessageSerializer[Any, ByteString]],
              responseSerializer.asInstanceOf[MessageSerializer[Any, ByteString]]
            )
          }
        }
      } else None
    })
  }

  private val inMemoryBodyParser = BodyParser { req =>
    val contentLength = req.headers.get(HeaderNames.CONTENT_LENGTH)
    val hasBody = contentLength.filter(_ != "0").orElse(req.headers.get(HeaderNames.TRANSFER_ENCODING)).isDefined
    if (hasBody) {
      BodyParsers.parse.maxLength(httpConfiguration.parser.maxMemoryBuffer, BodyParser { _ =>
        Accumulator(Sink.fold[ByteString, ByteString](ByteString.empty)((state, bs) => state ++ bs)).map(Right.apply)
      }).apply(req)
    } else {
      Accumulator.done(Right(Right(ByteString.empty)))
    }
  }

  /**
   * Create the action.
   */
  protected def action[Request, Response](
    call:               Call[Request, Response],
    descriptor:         Descriptor,
    serviceCall:        ServiceCall[Request, Response],
    requestSerializer:  MessageSerializer[Request, ByteString],
    responseSerializer: MessageSerializer[Response, ByteString]
  ): EssentialAction

  /**
   * Create an action to handle the given service call. All error handling is done here.
   */
  protected final def createAction[Request, Response](
    call:               Call[Request, Response],
    descriptor:         Descriptor,
    serviceCall:        ServiceCall[Request, Response],
    requestSerializer:  MessageSerializer[Request, ByteString],
    responseSerializer: MessageSerializer[Response, ByteString]
  ): EssentialAction = EssentialAction { request =>
    val unfilteredHeader = toLagomRequestHeader(request)
    val filteredHeaders = headerFilterTransformServerRequest(descriptorHeaderFilter(descriptor), unfilteredHeader)
    try {
      handleServiceCall(serviceCall, descriptor, requestSerializer, responseSerializer, filteredHeaders, request).recover {
        case NonFatal(e) =>
          logException(e, descriptor, call)
          exceptionToResult(descriptor, filteredHeaders, e)
      }
    } catch {
      case NonFatal(e) =>
        logException(e, descriptor, call)
        Accumulator.done(exceptionToResult(descriptor, filteredHeaders, e))
    }
  }

  /**
   * Handle a regular service call, that is, either a ServerServiceCall, or a plain ServiceCall.
   */
  private def handleServiceCall[Request, Response](
    serviceCall: ServiceCall[Request, Response], descriptor: Descriptor,
    requestSerializer: MessageSerializer[Request, ByteString], responseSerializer: MessageSerializer[Response, ByteString],
    requestHeader: RequestHeader, playRequestHeader: PlayRequestHeader
  ): Accumulator[ByteString, Result] = {
    val requestMessageDeserializer = messageSerializerDeserializer(requestSerializer, messageHeaderProtocol(requestHeader))

    // Buffer the body in memory
    inMemoryBodyParser(playRequestHeader).mapFuture {
      // Error handling.
      // If it's left of a result (which this particular body parser should never return) just return that result.
      case Left(result)   => Future.successful(result)
      // If the payload was too large, throw that exception (exception serializer will handle it later).
      case Right(Left(_)) => throw newPayloadTooLarge("Request body larger than " + httpConfiguration.parser.maxMemoryBuffer)
      // Body was successfully buffered.
      case Right(Right(body)) =>
        // Deserialize request
        val request = negotiatedDeserializerDeserialize(requestMessageDeserializer, body)

        // Invoke the service call
        invokeServiceCall(serviceCall, requestHeader, request).map {
          case (responseHeader, response) =>
            // Serialize the response body
            val serializer = messageSerializerSerializerForResponse(responseSerializer, requestHeaderAcceptedResponseProtocols(requestHeader))
            val responseBody = negotiatedSerializerSerialize(serializer, response)

            // If no content type was defined by the service call itself, then replace the protocol with the
            // serializers protocol
            val rhWithProtocol = if (messageProtocolContentType(messageHeaderProtocol(responseHeader)).isEmpty) {
              responseHeaderWithProtocol(responseHeader, negotiatedSerializerProtocol(serializer))
            } else responseHeader

            // Transform the response header
            val transformedResponseHeader = headerFilterTransformServerResponse(
              descriptorHeaderFilter(descriptor),
              rhWithProtocol,
              requestHeader
            )

            // And create the result
            Results.Status(responseHeaderStatus(transformedResponseHeader)).sendEntity(Strict(
              responseBody,
              messageProtocolToContentTypeHeader(messageHeaderProtocol(transformedResponseHeader))
            )).withHeaders(toResponseHeaders(transformedResponseHeader): _*)
        }
    }
  }

  private def logException(exc: Throwable, descriptor: Descriptor, call: Call[_, _]) = {
    def log = Logger(descriptorName(descriptor))

    val cause = exc match {
      case c: CompletionException => c.getCause
      case e                      => e
    }
    maybeLogException(cause, log, call)
  }

  protected def maybeLogException(exc: Throwable, log: => Logger, call: Call[_, _])

  /**
   * Converts an exception to a result, using the configured exception serializer.
   */
  private def exceptionToResult(descriptor: Descriptor, requestHeader: RequestHeader, e: Throwable): Result = {
    val acceptedResponseProtocols = requestHeaderAcceptedResponseProtocols(requestHeader)
    val rawExceptionMessage = exceptionSerializerSerialize(descriptorExceptionSerializer(descriptor), e, acceptedResponseProtocols)
    val responseHeader = headerFilterTransformServerResponse(
      descriptorHeaderFilter(descriptor),
      rawExceptionMessageToResponseHeader(rawExceptionMessage), requestHeader
    )

    Results.Status(responseHeaderStatus(responseHeader)).sendEntity(Strict(
      rawExceptionMessageMessage(rawExceptionMessage),
      messageProtocolToContentTypeHeader(messageHeaderProtocol(responseHeader))
    )).withHeaders(toResponseHeaders(responseHeader): _*)
  }

  /**
   * Convert a Play (Scala) request header to a Lagom request header without invoking Lagom HeaderFilters.
   */
  private def toLagomRequestHeader(rh: PlayRequestHeader): RequestHeader = {
    val stringToTuples: Map[String, immutable.Seq[(String, String)]] = rh.headers.toMap.map {
      case (key, values) => key.toLowerCase(Locale.ENGLISH) -> values.map(key -> _).to[immutable.Seq]
    }
    newRequestHeader(
      newMethod(rh.method),
      URI.create(rh.uri),
      messageProtocolFromContentTypeHeader(rh.headers.get(HeaderNames.CONTENT_TYPE)),
      rh.acceptedTypes.map { mediaType =>
        newMessageProtocol(
          Some(s"${mediaType.mediaType}/${mediaType.mediaSubType}"),
          mediaType.parameters.find(_._1 == "charset").flatMap(_._2), None
        )
      }.to[immutable.Seq],
      None,
      stringToTuples
    )
  }

  /**
   * Convert a Lagom response header to a Play response header map.
   */
  private def toResponseHeaders(responseHeader: ResponseHeader): Seq[(String, String)] = {
    messageHeaderHeaders(responseHeader).toSeq.filter(_._2.nonEmpty).map {
      case (key, values) => values.head
    }
  }

  /**
   * Handle a service call as a WebSocket.
   */
  private def websocket[Request, Response](
    call:               Call[Request, Response],
    descriptor:         Descriptor,
    serviceCall:        ServiceCall[Request, Response],
    requestSerializer:  MessageSerializer[Request, _],
    responseSerializer: MessageSerializer[Response, _]
  ): WebSocket = WebSocket.acceptOrResult[Message, Message] { rh =>

    val unfilteredHeader: RequestHeader = toLagomRequestHeader(rh)
    val requestHeader = headerFilterTransformServerRequest(descriptorHeaderFilter(descriptor), unfilteredHeader)

    val requestProtocol = messageHeaderProtocol(requestHeader)
    val acceptHeaders = requestHeaderAcceptedResponseProtocols(requestHeader)

    // We need to return a future. Also, we need to handle any exceptions thrown. By doing this asynchronously, we can
    // ensure all exceptions are handled in one place, in the future recover block.
    Future {
      // A promise for request body, which may be a stream or a single message, depending on the service call.
      // This will be redeemed by the incoming sink, and on redemption, we'll be able to invoke the service call.
      val requestPromise = Promise[Request]()

      // This promise says when the incoming stream has cancelled. We block the cancel of the incoming stream,
      // but and don't actually close the socket until the outgoing stream finishes.  However, for strict outgoing
      // responses, that will be immediately after that response has been sent, so in that case we need to ensure
      // that that outgoing stream close is delayed until the incoming cancels.
      val incomingCancelled = Promise[None.type]()

      val requestMessageDeserializer = messageSerializerDeserializer(requestSerializer, requestProtocol)
      val responseMessageSerializer = messageSerializerSerializerForResponse(responseSerializer, acceptHeaders)

      // The incoming sink is the sink that we're going to return to Play to handle incoming websocket messages.
      val incomingSink: Sink[ByteString, _] = if (messageSerializerIsStreamed(requestSerializer)) {
        // If it's a streamed message serializer, we return a sink that when materialized (which effectively represents
        // when the WebSocket handshake is complete), will redeem the request promise with a source that is hooked up
        // directly to this sink.
        val deserializer = requestMessageDeserializer.asInstanceOf[NegotiatedDeserializer[Request, AkkaStreamsSource[ByteString, _]]]

        val captureCancel = Flow[ByteString].via(new GraphStage[FlowShape[ByteString, ByteString]] {

          val in = Inlet[ByteString]("CaptureCancelIn")
          val out = Outlet[ByteString]("CaptureCancelOut")

          override def shape = FlowShape(in, out)

          override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
            setHandler(in, new InHandler {
              override def onPush(): Unit = push(out, grab(in))
            })
            setHandler(out, new OutHandler {
              override def onPull(): Unit = pull(in)

              override def onDownstreamFinish(): Unit = {
                incomingCancelled.success(None)
                cancel(in)
              }
            })
          }
        })

        AkkaStreams.ignoreAfterCancellation via captureCancel to Sink.asPublisher[ByteString](fanout = false).mapMaterializedValue { publisher =>
          requestPromise.complete(Try(negotiatedDeserializerDeserialize(deserializer, toAkkaStreamsSource(Source.fromPublisher(publisher)))))
        }
      } else {
        // If it's a strict message serializer, we return a sink that reads one message, deserializes that message, and
        // then redeems the request promise with that message.
        val deserializer = requestMessageDeserializer.asInstanceOf[NegotiatedDeserializer[Request, ByteString]]

        if (messageSerializerIsUsed(requestSerializer)) {
          AkkaStreams.ignoreAfterCancellation[ByteString]
            .toMat(Sink.headOption)(Keep.right)
            .mapMaterializedValue(_.map { maybeBytes =>
              val bytes = maybeBytes.getOrElse(ByteString.empty)
              requestPromise.complete(Try(negotiatedDeserializerDeserialize(deserializer, bytes)))
              incomingCancelled.success(None)
            })
        } else {
          // If it's not used, don't wait for the first message (because no message will come), just ignore the
          // whole stream
          requestPromise.complete(Try(negotiatedDeserializerDeserialize(deserializer, ByteString.empty)))
          incomingCancelled.success(None)
          Sink.ignore
        }
      }

      // The outgoing source is the source that we're going to return to Play to produce outgoing websocket messages.
      val outgoingSource: Source[ByteString, _] = Source.asSubscriber[ByteString].mapMaterializedValue { subscriber =>
        (for {
          // First we need to get the request
          request <- requestPromise.future
          // Then we can invoke the service call
          (responseHeader, response) <- invokeServiceCall(serviceCall, requestHeader, request)
        } yield {
          if (!responseHeaderIsDefault(responseHeader)) {
            Logger.warn("Response header contains a custom status code and/or custom protocol and/or custom headers, " +
              "but this was invoked by a transport (eg WebSockets) that does not allow sending custom headers. " +
              "This response header will be ignored: " + responseHeader)
          }

          val outgoingSource = if (messageSerializerIsStreamed(responseSerializer)) {
            // If streamed, then the source is just the source stream.
            val serializer = responseMessageSerializer.asInstanceOf[NegotiatedSerializer[Response, AkkaStreamsSource[ByteString, NotUsed]]]
            akkaStreamsSourceAsScala(negotiatedSerializerSerialize(serializer, response))
          } else {
            // If strict, then the source will be a single source of the response message, concatenated with a lazy
            // empty source so that the incoming stream is still able to receive messages.
            val serializer = responseMessageSerializer.asInstanceOf[NegotiatedSerializer[Response, ByteString]]
            Source.single(negotiatedSerializerSerialize(serializer, response)).concat(
              // The outgoing is responsible for closing, however when the response is strict, this needs to be in
              // response to the incoming cancelling, since otherwise it will always close immediately after
              // sending the strict response. We can't just let the incoming cancel directly, because that
              // introduces a race condition, the strict message from the Source.single may not reach the connection
              // before the cancel is received and closes the connection.
              Source.maybe[ByteString].mapMaterializedValue(_.completeWith(incomingCancelled.future))
            )
          }

          // Connect the source to the subscriber
          outgoingSource.runWith(Sink.fromSubscriber(subscriber))
        }).recover {
          case NonFatal(e) =>
            Source.failed(e).runWith(Sink.fromSubscriber(subscriber))
        }
      }

      // Todo: Add headers/content-type to response when https://github.com/playframework/playframework/issues/5322 is
      // implemented
      // First, a flow that converts Play WebSocket messages to ByteStrings. Then it goes through our incomingSink and
      // outgoingSource, then gets mapped back to Play WebSocket messages.
      Right(Flow[Message].collect {
        case TextMessage(text)    => ByteString(text)
        case BinaryMessage(bytes) => bytes
        case CloseMessage(statusCode, reason) if statusCode.exists(_ != 1000) =>
          // This is an error, deserialize and throw
          val messageBytes = if (messageProtocolIsText(requestProtocol)) {
            ByteString(reason, messageProtocolCharset(requestProtocol).getOrElse("utf-8"))
          } else {
            Try(ByteString(Base64.getDecoder.decode(reason))).toOption.getOrElse(ByteString(reason))
          }
          throw exceptionSerializerDeserializeWebSocketException(
            descriptorExceptionSerializer(descriptor),
            statusCode.get, requestProtocol, messageBytes
          )
      } via Flow.fromSinkAndSource(incomingSink, outgoingSource) via Flow[ByteString].map { bytes =>
        val responseProtocol = negotiatedSerializerProtocol(responseMessageSerializer)
        if (messageProtocolIsText(responseProtocol)) {
          TextMessage(bytes.decodeString(messageProtocolCharset(responseProtocol).getOrElse("utf-8")))
        } else {
          BinaryMessage(bytes)
        }
      }.recover {
        case NonFatal(e) =>
          logException(e, descriptor, call)
          exceptionToCloseMessage(e, acceptHeaders)
      })
    }.recover {
      case NonFatal(e) =>
        logException(e, descriptor, call)
        Left(exceptionToResult(descriptor, requestHeader, e))
    }
  }

  /** Convert an exception to a close message */
  private def exceptionToCloseMessage(exception: Throwable, acceptHeaders: immutable.Seq[MessageProtocol]) = {
    val exceptionSerializer = descriptorExceptionSerializer(descriptor)
    // First attempt to serialize the exception using the exception serializer
    val rawExceptionMessage = exceptionSerializerSerialize(exceptionSerializer, exception, acceptHeaders)

    val safeExceptionMessage = if (rawExceptionMessageMessageAsText(rawExceptionMessage).length > WebSocketControlFrameMaxLength) {
      // If the serializer produced an error message that was too big for WebSockets, fall back to a simpler error
      // message.
      val truncatedExceptionMessage = exceptionSerializerSerialize(
        exceptionSerializer,
        newTransportException(
          rawExceptionMessageErrorCode(rawExceptionMessage),
          "Error message truncated"
        ), acceptHeaders
      )

      // It may be that the serialized exception message with no detail is still too big for a WebSocket, fall back to
      // plain text message.
      if (rawExceptionMessageMessageAsText(truncatedExceptionMessage).length > WebSocketControlFrameMaxLength) {
        newRawExceptionMessage(
          rawExceptionMessageErrorCode(rawExceptionMessage),
          newMessageProtocol(Some("text/plain"), Some("utf-8"), None),
          ByteString.fromString("Error message truncated")
        )
      } else truncatedExceptionMessage
    } else rawExceptionMessage

    CloseMessage(Some(rawExceptionMessageWebSocketCode(safeExceptionMessage)), rawExceptionMessageMessageAsText(safeExceptionMessage))
  }

  /**
   * Supply the request header to the service call
   */
  protected def invokeServiceCall[Request, Response](
    serviceCall:   ServiceCall[Request, Response],
    requestHeader: RequestHeader, request: Request
  ): Future[(ResponseHeader, Response)]

}
