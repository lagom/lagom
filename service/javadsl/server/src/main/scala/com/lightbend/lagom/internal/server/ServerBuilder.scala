/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import java.net.URI
import java.util.function.{ BiFunction, Function => JFunction }
import java.util.{ Base64, Optional }
import javax.inject.{ Singleton, Provider, Inject }
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.stream.javadsl.{ Source => JSource }
import akka.stream.stage.{ TerminationDirective, SyncDirective, Context, PushStage }
import akka.util.ByteString
import com.lightbend.lagom.internal.api._
import com.lightbend.lagom.javadsl.api.Descriptor.{ RestCallId, Call }
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.{ NegotiatedSerializer, NegotiatedDeserializer }
import com.lightbend.lagom.javadsl.api.transport._
import com.lightbend.lagom.javadsl.api._
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.jackson.{ JacksonExceptionSerializer, JacksonSerializerFactory }
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport.{ ClassServiceBinding, InstanceServiceBinding }
import com.lightbend.lagom.javadsl.server.{ PlayServiceCall, ServiceGuiceSupport }
import org.pcollections.{ HashTreePMap, PSequence, TreePVector }
import play.api.mvc.{ RequestHeader => PlayRequestHeader, ResponseHeader => _, _ }
import play.api.{ Logger, Environment }
import play.api.http.HttpEntity.Strict
import play.api.http.websocket.{ CloseMessage, BinaryMessage, TextMessage, Message }
import play.api.http.{ HeaderNames, HttpConfiguration }
import play.api.inject.Injector
import play.api.libs.streams.{ AkkaStreams, Accumulator }
import play.api.routing.SimpleRouter
import play.api.routing.Router.Routes
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.{ Promise, ExecutionContext, Future }
import scala.util.{ Try, Right }
import scala.util.control.NonFatal
import java.util.concurrent.CompletionException

/**
 * Turns a service implementation and descriptor into a Play router
 */
class ServerBuilder @Inject() (environment: Environment, httpConfiguration: HttpConfiguration,
                               jacksonSerializerFactory:   JacksonSerializerFactory,
                               jacksonExceptionSerializer: JacksonExceptionSerializer)(implicit ec: ExecutionContext, mat: Materializer) {

  /**
   * Create a router for the given services.
   *
   * @param services An array of service interfaces to implementations.
   * @return The services.
   */
  def resolveServices(services: Seq[(Class[_], Any)]): ResolvedServices = {
    val resolvedDescriptors = services.map {
      case (interface, serviceImpl) if classOf[Service].isAssignableFrom(interface) =>
        val descriptor = ServiceReader.readServiceDescriptor(
          environment.classLoader,
          interface.asSubclass(classOf[Service])
        )
        ResolvedService(interface.asInstanceOf[Class[Any]], serviceImpl, resolveDescriptor(descriptor))
      case (interface, _) =>
        throw new IllegalArgumentException(s"Don't know how to load services that don't implement Service: $interface")
    }
    ResolvedServices(resolvedDescriptors)
  }

  /**
   * Resolve the given descriptor to the implementation of the service.
   */
  def resolveDescriptor(descriptor: Descriptor): Descriptor = {
    ServiceReader.resolveServiceDescriptor(descriptor, environment.classLoader,
      Map(JacksonPlaceholderSerializerFactory -> jacksonSerializerFactory),
      Map(JacksonPlaceholderExceptionSerializer -> jacksonExceptionSerializer))
  }

  /**
   * Create a service info for the given interface.
   *
   * @param interface The interface to create the service info for.
   * @return The service info.
   */
  def createServiceInfo(interface: Class[_]): ServiceInfo = {
    if (classOf[Service].isAssignableFrom(interface)) {
      val descriptor = ServiceReader.readServiceDescriptor(
        environment.classLoader,
        interface.asSubclass(classOf[Service])
      )
      new ServiceInfo(descriptor.name())
    } else {
      throw new IllegalArgumentException(s"Don't know how to load services that don't implement Service: $interface")
    }
  }
}

case class ResolvedServices(services: Seq[ResolvedService[_]])
case class ResolvedService[T](interface: Class[T], service: T, descriptor: Descriptor)

@Singleton
class ResolvedServicesProvider(bindings: Seq[ServiceGuiceSupport.ServiceBinding[_]]) extends Provider[ResolvedServices] {
  def this(bindings: Array[ServiceGuiceSupport.ServiceBinding[_]]) = this(bindings.toSeq)

  @Inject var serverBuilder: ServerBuilder = null
  @Inject var injector: Injector = null

  lazy val get = {
    serverBuilder.resolveServices(bindings.map {
      case instance: InstanceServiceBinding[_] => (instance.serviceInterface, instance.service)
      case clazz: ClassServiceBinding[_]       => (clazz.serviceInterface, injector.instanceOf(clazz.serviceImplementation))
    })
  }
}

@Singleton
class ServiceRouter @Inject() (resolvedServices: ResolvedServices, httpConfiguration: HttpConfiguration)(implicit ec: ExecutionContext, mat: Materializer) extends SimpleRouter {

  private val serviceRouters = resolvedServices.services.map { service =>
    new SingleServiceRouter(service.descriptor, service.descriptor.calls.asScala.map { call =>
      ServiceRoute(call, service.service)
    }, httpConfiguration)
  }

  override val routes: Routes =
    serviceRouters.foldLeft(PartialFunction.empty[PlayRequestHeader, Handler])((routes, router) => routes.orElse(router.routes))
  override def documentation: Seq[(String, String, String)] = serviceRouters.flatMap(_.documentation)
}

case class ServiceRoute(call: Descriptor.Call[_, _], service: Any) {
  val path = Path.fromCallId(call.callId)
  val method = call.callId match {
    case rest: RestCallId => rest.method
    case _ => if (call.requestSerializer.isUsed) {
      Method.POST
    } else {
      Method.GET
    }
  }
  val isWebSocket = call.requestSerializer.isInstanceOf[StreamedMessageSerializer[_]] ||
    call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]

  val holder: MethodServiceCallHolder = call.serviceCallHolder() match {
    case holder: MethodServiceCallHolder => holder
  }

  def createServiceCall(params: Seq[Seq[String]]) = {
    holder.create(service, params).asInstanceOf[ServiceCall[Any, Any]]
  }
}

object SingleServiceRouter {
  /** RFC 6455 Section 5.5 - maximum control frame size is 125 bytes */
  val WebSocketControlFrameMaxLength = 125
}

class SingleServiceRouter(descriptor: Descriptor, serviceRoutes: Seq[ServiceRoute], httpConfiguration: HttpConfiguration)(implicit ec: ExecutionContext, mat: Materializer) extends SimpleRouter {

  import SingleServiceRouter._

  /**
   * The routes partial function.
   */
  override def routes: Routes = Function.unlift { request =>
    val requestHeader = toRequestHeader(request)
    val isWebSocket = request.headers.get(HeaderNames.UPGRADE).contains("websocket")
    serviceRoutes.collectFirst(Function.unlift { route =>
      // We match by method, but since we ignore the method if it's a WebSocket (because WebSockets require that GET
      // is used) we also match if it's a WebSocket request and this can be handled as a WebSocket.
      if (route.method.name == request.method || (isWebSocket && route.isWebSocket)) {

        route.path.extract(requestHeader.uri.getRawPath, request.queryString).map { params =>
          val serviceCall = route.createServiceCall(params)

          // If both request and response are strict, handle it using an action, otherwise handle it using a websocket
          (route.call.requestSerializer, route.call.responseSerializer) match {
            case (strictRequest: StrictMessageSerializer[Any], strictResponse: StrictMessageSerializer[Any]) =>
              action(route.call.asInstanceOf[Call[Any, Any]], descriptor, strictRequest, strictResponse,
                requestHeader, serviceCall)
            case _ => websocket(route.call.asInstanceOf[Call[Any, Any]], descriptor, requestHeader, serviceCall)
          }
        }
      } else None
    })
  }

  private val inMemoryBodyParser = BodyParsers.parse.maxLength(httpConfiguration.parser.maxMemoryBuffer, BodyParser { req =>
    Accumulator(Sink.fold[ByteString, ByteString](ByteString.empty)((state, bs) => state ++ bs)).map(Right.apply)
  })

  /**
   * Create the action.
   */
  private def action[Request, Response](
    call: Call[Request, Response], descriptor: Descriptor,
    requestSerializer: StrictMessageSerializer[Request], responseSerializer: StrictMessageSerializer[Response],
    requestHeader: RequestHeader, serviceCall: ServiceCall[Request, Response]
  ): EssentialAction = {

    serviceCall match {
      // If it's a Play service call, then rather than creating the action directly, we let it create the action, and
      // pass it a callback that allows it to convert a service call into an action.
      case playServiceCall: PlayServiceCall[Request, Response] =>
        playServiceCall.invoke(
          new java.util.function.Function[ServiceCall[Request, Response], play.mvc.EssentialAction] {
            override def apply(serviceCall: ServiceCall[Request, Response]): play.mvc.EssentialAction = {
              createAction(serviceCall, call, descriptor, requestSerializer, responseSerializer, requestHeader).asJava
            }
          }
        )
      case _ =>
        createAction(serviceCall, call, descriptor, requestSerializer, responseSerializer, requestHeader)
    }
  }

  /**
   * Create an action to handle the given service call. All error handling is done here.
   */
  private def createAction[Request, Response](
    serviceCall: ServiceCall[Request, Response], call: Call[Request, Response], descriptor: Descriptor,
    requestSerializer: StrictMessageSerializer[Request], responseSerializer: StrictMessageSerializer[Response],
    requestHeader: RequestHeader
  ) = EssentialAction { request =>
    try {
      handleServiceCall(serviceCall, descriptor, requestSerializer, responseSerializer, requestHeader, request).recover {
        case NonFatal(e) =>
          logException(e, descriptor, call)
          exceptionToResult(descriptor.exceptionSerializer, requestHeader, e)
      }
    } catch {
      case NonFatal(e) =>
        logException(e, descriptor, call)
        Accumulator.done(exceptionToResult(
          descriptor.exceptionSerializer,
          requestHeader, e
        ))
    }
  }

  /**
   * Handle a regular service call, that is, either a ServerServiceCall, or a plain ServiceCall.
   */
  private def handleServiceCall[Request, Response](
    serviceCall: ServiceCall[Request, Response], descriptor: Descriptor,
    requestSerializer: StrictMessageSerializer[Request], responseSerializer: StrictMessageSerializer[Response],
    requestHeader: RequestHeader, playRequestHeader: PlayRequestHeader
  ): Accumulator[ByteString, Result] = {
    val requestMessageDeserializer = requestSerializer.deserializer(requestHeader.protocol)

    // Buffer the body in memory
    inMemoryBodyParser(playRequestHeader).mapFuture {
      // Error handling.
      // If it's left of a result (which this particular body parser should never return) just return that result.
      case Left(result)   => Future.successful(result)
      // If the payload was too large, throw that exception (exception serializer will handle it later).
      case Right(Left(_)) => throw new PayloadTooLarge("Request body larger than " + httpConfiguration.parser.maxMemoryBuffer)
      // Body was successfully buffered.
      case Right(Right(body)) =>
        // Deserialize request
        val request = requestMessageDeserializer.deserialize(body)

        // Invoke the service call
        invokeServiceCall(serviceCall, requestHeader, request).map {
          case (responseHeader, response) =>
            // Serialize the response body
            val serializer = responseSerializer.serializerForResponse(requestHeader.acceptedResponseProtocols())
            val responseBody = serializer.serialize(response)

            // If no content type was defined by the service call itself, then replace the protocol with the
            // serializers protocol
            val responseHeaderWithProtocol = if (!responseHeader.protocol.contentType.isPresent) {
              responseHeader.withProtocol(serializer.protocol())
            } else responseHeader

            // Transform the response header
            val transformedResponseHeader = descriptor.headerFilter.transformServerResponse(
              responseHeaderWithProtocol,
              requestHeader
            )

            // And create the result
            Results.Status(transformedResponseHeader.status).sendEntity(Strict(
              responseBody,
              transformedResponseHeader.protocol.toContentTypeHeader.asScala
            )).withHeaders(toResponseHeaders(transformedResponseHeader): _*)
        }
    }
  }

  private def logException(exc: Throwable, descriptor: Descriptor, call: Call[_, _]) = {
    def log = Logger(descriptor.name)
    val cause = exc match {
      case c: CompletionException => c.getCause
      case e                      => e
    }
    cause match {
      case _: NotFound | _: Forbidden => // no logging
      case e @ (_: UnsupportedMediaType | _: PayloadTooLarge | _: NotAcceptable) =>
        log.warn(e.getMessage)
      case e =>
        log.error(s"Exception in ${call.callId()}", e)
    }
  }

  /**
   * Converts an exception to a result, using the configured exception serializer.
   */
  private def exceptionToResult(exceptionSerializer: ExceptionSerializer, requestHeader: RequestHeader, e: Throwable): Result = {
    val rawExceptionMessage = exceptionSerializer.serialize(e, requestHeader.acceptedResponseProtocols)
    val responseHeader = descriptor.headerFilter.transformServerResponse(new ResponseHeader(
      rawExceptionMessage.errorCode.http,
      rawExceptionMessage.protocol,
      HashTreePMap.empty()
    ), requestHeader)

    Results.Status(responseHeader.status).sendEntity(Strict(
      rawExceptionMessage.message,
      responseHeader.protocol.toContentTypeHeader.asScala
    )).withHeaders(toResponseHeaders(responseHeader): _*)
  }

  /**
   * Convert a Play (Scala) request header to a Lagom request header.
   */
  private def toRequestHeader(rh: PlayRequestHeader): RequestHeader = {
    val requestHeader = new RequestHeader(
      new Method(rh.method),
      URI.create(rh.uri),
      MessageProtocol.fromContentTypeHeader(rh.headers.get(HeaderNames.CONTENT_TYPE).asJava),
      TreePVector.from(rh.acceptedTypes.map { mediaType =>
        new MessageProtocol(
          Optional.of(s"${mediaType.mediaType}/${mediaType.mediaSubType}"),
          mediaType.parameters.find(_._1 == "charset").flatMap(_._2).asJava, Optional.empty()
        )
      }.asJava),
      Optional.empty(),
      rh.headers.toMap.foldLeft(HashTreePMap.empty[String, PSequence[String]]) {
        case (map, (name, values)) => map.plus(name, TreePVector.from(values.asJava))
      }
    )
    descriptor.headerFilter.transformServerRequest(requestHeader)
  }

  /**
   * Convert a Lagom response header to a Play response header map.
   */
  private def toResponseHeaders(responseHeader: ResponseHeader) = {
    responseHeader.headers().asScala.toSeq.filter(!_._2.isEmpty).map {
      case (key, values) => key -> values.asScala.head
    }
  }

  /**
   * Handle a service call as a WebSocket.
   */
  private def websocket[Request, Response](call: Call[Request, Response], descriptor: Descriptor,
                                           requestHeader: RequestHeader, serviceCall: ServiceCall[Request, Response]): WebSocket = WebSocket.acceptOrResult { rh =>

    val requestProtocol = requestHeader.protocol
    val acceptHeaders = requestHeader.acceptedResponseProtocols

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

      val requestMessageDeserializer = call.requestSerializer.deserializer(requestProtocol)
      val responseMessageSerializer = call.responseSerializer.serializerForResponse(acceptHeaders)

      // The incoming sink is the sink that we're going to return to Play to handle incoming websocket messages.
      val incomingSink: Sink[ByteString, _] = call.requestSerializer match {
        // If it's a strict message serializer, we return a sink that reads one message, deserializes that message, and
        // then redeems the request promise with that message.
        case strict: StrictMessageSerializer[Request] =>
          val deserializer = requestMessageDeserializer.asInstanceOf[NegotiatedDeserializer[Request, ByteString]]

          if (strict.isUsed) {
            AkkaStreams.ignoreAfterCancellation[ByteString]
              .toMat(Sink.headOption)(Keep.right)
              .mapMaterializedValue(_.map { maybeBytes =>
                val bytes = maybeBytes.getOrElse(ByteString.empty)
                requestPromise.complete(Try(deserializer.deserialize(bytes)))
                incomingCancelled.success(None)
              })
          } else {
            // If it's not used, don't wait for the first message (because no message will come), just ignore the
            // whole stream
            requestPromise.complete(Try(deserializer.deserialize(ByteString.empty)))
            incomingCancelled.success(None)
            Sink.ignore
          }
        // If it's a streamed message serializer, we return a sink that when materialized (which effectively represents
        // when the WebSocket handshake is complete), will redeem the request promise with a source that is hooked up
        // directly to this sink.
        case streamed: StreamedMessageSerializer[_] =>
          val deserializer = requestMessageDeserializer.asInstanceOf[NegotiatedDeserializer[Request, JSource[ByteString, _]]]

          val captureCancel = Flow[ByteString].transform(() => new PushStage[ByteString, ByteString] {
            override def onDownstreamFinish(ctx: Context[ByteString]): TerminationDirective = {
              incomingCancelled.success(None)
              ctx.finish()
            }
            override def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = ctx.push(elem)
          })

          AkkaStreams.ignoreAfterCancellation via captureCancel to Sink.asPublisher[ByteString](fanout = false).mapMaterializedValue { publisher =>
            requestPromise.complete(Try(deserializer.deserialize(JSource.fromPublisher(publisher))))
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
          if (responseHeader != ResponseHeader.OK) {
            Logger.warn("Response header contains a custom status code and/or custom protocol and/or custom headers, " +
              "but this was invoked by a transport (eg WebSockets) that does not allow sending custom headers. " +
              "This response header will be ignored: " + responseHeader)
          }

          val outgoingSource = call.responseSerializer() match {
            // If strict, then the source will be a single source of the response message, concatenated with a lazy
            // empty source so that the incoming stream is still able to receive messages.
            case strict: StrictMessageSerializer[Response] =>
              val serializer = responseMessageSerializer.asInstanceOf[NegotiatedSerializer[Response, ByteString]]
              Source.single(serializer.serialize(response)).concat(
                // The outgoing is responsible for closing, however when the response is strict, this needs to be in
                // response to the incoming cancelling, since otherwise it will always close immediately after
                // sending the strict response. We can't just let the incoming cancel directly, because that
                // introduces a race condition, the strict message from the Source.single may not reach the connection
                // before the cancel is received and closes the connection.
                Source.maybe[ByteString].mapMaterializedValue(_.completeWith(incomingCancelled.future))
              )
            // If streamed, then the source is just the source stream.
            case streamed: StreamedMessageSerializer[Response] =>
              val serializer = responseMessageSerializer.asInstanceOf[NegotiatedSerializer[Response, JSource[ByteString, _]]]
              serializer.serialize(response).asScala
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
          val messageBytes = if (requestProtocol.isText) {
            ByteString(reason, requestProtocol.charset.get)
          } else {
            Try(ByteString(Base64.getDecoder.decode(reason))).toOption.getOrElse(ByteString(reason))
          }
          val rawExceptionMessage = new RawExceptionMessage(
            TransportErrorCode.fromWebSocket(statusCode.get),
            requestProtocol, messageBytes
          )
          throw descriptor.exceptionSerializer.deserialize(rawExceptionMessage)
      } via Flow.fromSinkAndSource(incomingSink, outgoingSource) via Flow[ByteString].map { bytes =>
        if (responseMessageSerializer.protocol.isText) {
          TextMessage(bytes.decodeString(responseMessageSerializer.protocol.charset.get))
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
        Left(exceptionToResult(descriptor.exceptionSerializer, requestHeader, e))
    }
  }

  /** Convert an exception to a close message */
  private def exceptionToCloseMessage(exception: Throwable, acceptHeaders: PSequence[MessageProtocol]) = {
    // First attempt to serialize the exception using the exception serializer
    val rawExceptionMessage = descriptor.exceptionSerializer.serialize(exception, acceptHeaders)

    val safeExceptionMessage = if (rawExceptionMessage.message().size > WebSocketControlFrameMaxLength) {
      // If the serializer produced an error message that was too big for WebSockets, fall back to a simpler error
      // message.
      val truncatedExceptionMessage = descriptor.exceptionSerializer.serialize(
        new TransportException(
          rawExceptionMessage.errorCode(),
          new ExceptionMessage("Error message truncated", "")
        ), acceptHeaders
      )

      // It may be that the serialized exception message with no detail is still too big for a WebSocket, fall back to
      // plain text message.
      if (truncatedExceptionMessage.message().size > WebSocketControlFrameMaxLength) {
        new RawExceptionMessage(
          rawExceptionMessage.errorCode(),
          new MessageProtocol().withContentType("text/plain").withCharset("utf-8"),
          ByteString.fromString("Error message truncated")
        )
      } else truncatedExceptionMessage
    } else rawExceptionMessage

    CloseMessage(Some(safeExceptionMessage.errorCode().webSocket()), safeExceptionMessage.messageAsText())
  }

  /**
   * Supply the request header to the service call
   */
  def invokeServiceCall[Request, Response](
    serviceCall:   ServiceCall[Request, Response],
    requestHeader: RequestHeader, request: Request
  ): Future[(ResponseHeader, Response)] = {
    serviceCall match {
      case play: PlayServiceCall[_, _] =>
        throw new IllegalStateException("Can't invoke a Play service call for WebSockets or as a service call passed in by another Play service call: " + play)
      case _ =>
        serviceCall.handleRequestHeader(new JFunction[RequestHeader, RequestHeader] {
          override def apply(t: RequestHeader) = requestHeader
        }).handleResponseHeader(new BiFunction[ResponseHeader, Response, (ResponseHeader, Response)] {
          override def apply(header: ResponseHeader, response: Response) = header -> response
        }).invoke(request).toScala
    }
  }

}
