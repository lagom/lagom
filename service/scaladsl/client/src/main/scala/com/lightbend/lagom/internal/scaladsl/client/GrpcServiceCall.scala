/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import java.io.InputStream
import java.net.URI

import akka.actor.{ Actor, ActorSystem, Props }
import akka.util.{ ByteString, Timeout }
import com.google.common.io.ByteStreams
import com.lightbend.lagom.scaladsl.api.Descriptor.{ Call, NamedCallId }
import com.lightbend.lagom.scaladsl.api.deser.StrictMessageSerializer
import com.lightbend.lagom.scaladsl.api.{ Descriptor, ServiceCall, ServiceLocator }
import com.lightbend.lagom.scaladsl.api.transport.{ MessageProtocol, Method, RequestHeader, ResponseHeader }
import io.grpc.MethodDescriptor.{ Marshaller, MethodType }
import io.grpc._
import io.grpc.netty.NettyChannelBuilder
import io.netty.channel.EventLoopGroup

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.control.NonFatal

class GrpcServiceCall[Request, ResponseMessage, ServiceCallResponse](
  descriptor:           Descriptor,
  call:                 Call[Request, ResponseMessage],
  channelPool:          GrpcChannelPool,
  serviceLocator:       ServiceLocator,
  requestHeaderHandler: RequestHeader => RequestHeader,
  responseHandler:      (ResponseHeader, ResponseMessage) => ServiceCallResponse
)(implicit executionContext: ExecutionContext) extends ServiceCall[Request, ServiceCallResponse] {

  import GrpcServiceCall._

  private val methodDescriptor = MethodDescriptor.create[ByteString, ByteString](
    MethodType.UNARY,
    MethodDescriptor.generateFullMethodName(descriptor.name, call.callId.asInstanceOf[NamedCallId].name),
    ByteStringMarshaller, ByteStringMarshaller
  )

  override def invoke(request: Request): Future[ServiceCallResponse] = {

    serviceLocator.doWithService(descriptor.name, call) { uri =>

      channelPool.requestChannel(uri.getAuthority).flatMap { channel =>

        val promise = Promise[ServiceCallResponse]()
        promise.future.onComplete { _ =>
          channelPool.returnChannel(channel)
        }

        val requestSerializer = call.requestSerializer match {
          case strict: StrictMessageSerializer[Request] =>
            strict.serializerForRequest
        }

        val requestBytes = requestSerializer.serialize(request)

        val requestHeader = descriptor.headerFilter.transformClientRequest(requestHeaderHandler(
          RequestHeader(Method.POST, URI.create("/" + methodDescriptor.getFullMethodName),
            requestSerializer.protocol, call.requestSerializer.acceptResponseProtocols, None, Nil)
        ))

        // note - the gRPC library does not let us provide our own method, path, content type or user agent, so for now
        // we'll just ignore all of them, and there's no point in sending accept headers if we can't send a content type.
        val metadata = new Metadata()
        requestHeader.headers.foreach {
          case (key, value) => metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        }
        val clientCall = channel.newCall(methodDescriptor, CallOptions.DEFAULT)

        clientCall.start(new ClientCallListener(requestHeader, descriptor, call, responseHandler, promise), metadata)

        // Must request a message for unary calls
        clientCall.request(1)

        // gRPC impl sends the message after requesting the response, so we do the same
        clientCall.sendMessage(requestBytes)
        clientCall.halfClose()

        promise.future
      }
    }.map(_.getOrElse(throw new IllegalStateException("Service not found")))
  }

  override def handleRequestHeader(handler: (RequestHeader) => RequestHeader): ServiceCall[Request, ServiceCallResponse] =
    new GrpcServiceCall[Request, ResponseMessage, ServiceCallResponse](descriptor, call, channelPool, serviceLocator,
      requestHeaderHandler.andThen(handler), responseHandler)

  override def handleResponseHeader[T](handler: (ResponseHeader, ServiceCallResponse) => T): ServiceCall[Request, T] =
    new GrpcServiceCall[Request, ResponseMessage, T](descriptor, call, channelPool, serviceLocator, requestHeaderHandler,
      (header, message) => handler(header, responseHandler(header, message)))
}

private class ClientCallListener[Request, ResponseMessage, ServiceCallResponse](requestHeader: RequestHeader, descriptor: Descriptor,
                                                                                call: Call[Request, ResponseMessage], responseHandler: (ResponseHeader, ResponseMessage) => ServiceCallResponse,
                                                                                promise: Promise[ServiceCallResponse]) extends ClientCall.Listener[ByteString] {
  private var headers = immutable.Seq.empty[(String, String)]
  override def onHeaders(headers: Metadata): Unit = {
    try {
      import scala.collection.JavaConverters._
      this.headers = headers.keys.asScala.flatMap { key =>
        headers.getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)).asScala.map(key -> _)
      }.to[immutable.Seq]
    } catch {
      case NonFatal(e) =>
        promise.tryFailure(e)
        throw e
    }
  }
  override def onMessage(message: ByteString): Unit = {
    try {
      val deserializer = call.responseSerializer match {
        case strict: StrictMessageSerializer[ResponseMessage] =>
          strict.deserializer(MessageProtocol.empty)
      }
      val responseMessage = deserializer.deserialize(message)
      val responseHeader = descriptor.headerFilter.transformClientResponse(
        ResponseHeader(200, MessageProtocol.empty, headers), requestHeader
      )
      promise.success(responseHandler(responseHeader, responseMessage))
    } catch {
      case NonFatal(e) =>
        promise.tryFailure(e)
        throw e
    }
  }
  override def onClose(status: Status, trailers: Metadata): Unit = {
    if (promise.isCompleted) {
      // Ignore
    } else {
      if (status.isOk) {
        promise.failure(new RuntimeException("Server did not return any message for unary call"))
      } else {
        // todo translate to Lagom status code
        promise.failure(status.asException())
      }
    }
  }
}

private object GrpcServiceCall {
  val MarshallerBuffer = 8192

  val ByteStringMarshaller: Marshaller[ByteString] = new Marshaller[ByteString] {
    override def stream(value: ByteString): InputStream = value.iterator.asInputStream
    override def parse(stream: InputStream): ByteString = {
      val builder = ByteString.createBuilder
      ByteStreams.copy(stream, builder.asOutputStream)
      builder.result()
    }
  }
}

class GrpcChannelPool(actorSystem: ActorSystem, eventLoopGroup: EventLoopGroup) {
  import akka.pattern.ask
  import GrpcChannelPoolActor._
  import scala.concurrent.duration._

  private val actor = actorSystem.actorOf(Props(new GrpcChannelPoolActor(eventLoopGroup)))
  private implicit val timeout = Timeout(10.seconds)

  def requestChannel(authority: String): Future[Channel] = {
    (actor ? RequestChannel(authority)).mapTo[Channel]
  }
  def returnChannel(channel: Channel): Unit = {
    actor ! ReturnChannel(channel)
  }
}

private class GrpcChannelPoolActor(eventLoopGroup: EventLoopGroup) extends Actor {

  import GrpcChannelPoolActor._
  import scala.concurrent.duration._
  import context.dispatcher

  private class PooledChannel(val channel: ManagedChannel) {
    var outstanding: Int = 0
    var lastUse: Long = System.currentTimeMillis()
  }

  private var channels = Map.empty[String, PooledChannel]
  private var channelsByChannel = Map.empty[Channel, PooledChannel]
  private val scheduledTask = context.system.scheduler.schedule(30.seconds, 30.seconds, self, Tick)

  override def postStop(): Unit = {
    channels.foreach {
      case (_, channel) => channel.channel.shutdown()
    }
    scheduledTask.cancel()
  }

  override def receive: Receive = {
    case RequestChannel(authority) =>
      val channel = channels.get(authority) match {
        case Some(existing) => existing
        case None           => createChannel(authority)
      }
      channel.lastUse = System.currentTimeMillis()
      channel.outstanding += 1
      sender ! channel.channel

    case ReturnChannel(channel) =>
      channelsByChannel.get(channel).foreach { channel =>
        channel.outstanding -= 1
      }

    case Tick =>
      val expireIdle = System.currentTimeMillis() - 300000
      val cleanup = System.currentTimeMillis() - 600000
      channels.foreach {
        case (authority, channel) =>
          if (channel.outstanding == 0 && channel.lastUse < expireIdle) {
            channel.channel.shutdown()
            channels -= authority
            channelsByChannel -= channel.channel
          } else if (channel.lastUse < cleanup) {
            // todo log that a channel with outstanding requests is being cleaned up
            channel.channel.shutdown()
            channels -= authority
            channelsByChannel -= channel.channel
          }
      }
  }

  private def createChannel(authority: String): PooledChannel = {
    val channel = NettyChannelBuilder.forTarget(authority)
      .eventLoopGroup(eventLoopGroup)
      // This needs to be true before we work out how to support APLN
      .usePlaintext(true)
      .build()

    val pooledChannel = new PooledChannel(channel)

    channels += (authority -> pooledChannel)
    channelsByChannel += (channel -> pooledChannel)

    pooledChannel
  }
}

private object GrpcChannelPoolActor {
  case class RequestChannel(authority: String)
  case class ReturnChannel(channel: Channel)
  case object Tick
}
