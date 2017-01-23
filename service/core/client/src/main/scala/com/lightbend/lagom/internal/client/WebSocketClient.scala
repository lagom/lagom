/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.Locale

import akka.stream.scaladsl._
import akka.stream.stage.{ Context, PushStage, SyncDirective, TerminationDirective }
import akka.util.ByteString
import com.typesafe.netty.{ HandlerPublisher, HandlerSubscriber }
import com.lightbend.lagom.internal.NettyFutureConverters._
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ ByteBufHolder, Unpooled }
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel._
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http._
import io.netty.util.ReferenceCountUtil
import play.api.Environment
import play.api.http.HeaderNames
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import java.util.concurrent.atomic.AtomicReference

import akka.NotUsed
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor

import scala.collection.immutable

/**
 * A WebSocket client
 */
private[lagom] abstract class WebSocketClient(environment: Environment, eventLoop: EventLoopGroup,
                                              lifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) extends LagomServiceApiBridge {

  lifecycle.addStopHook(() => shutdown())

  // Netty channel groups are used to track open channels (connections), they automatically clean themselves up when
  // the channels close, and can be used to force all channels to close.
  val channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
  val client = new Bootstrap()
    .group(eventLoop)
    .channel(classOf[NioSocketChannel])
    .option(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE)
    .handler(new ChannelInitializer[SocketChannel] {
      def initChannel(ch: SocketChannel) = {
        channelGroup.add(ch)
        ch.pipeline().addLast(new HttpClientCodec, new HttpObjectAggregator(8192))
      }
    })

  /**
   * Connect to the given URI
   */
  def connect(exceptionSerializer: ExceptionSerializer, version: WebSocketVersion, requestHeader: RequestHeader,
              outgoing: Source[ByteString, NotUsed]): Future[(ResponseHeader, Source[ByteString, NotUsed])] = {

    val normalized = requestHeaderUri(requestHeader).normalize()
    val tgt = if (normalized.getPath == null || normalized.getPath.trim().isEmpty) {
      new URI(normalized.getScheme, normalized.getAuthority, "/", normalized.getQuery, normalized.getFragment)
    } else normalized

    val headers = new DefaultHttpHeaders()
    messageProtocolToContentTypeHeader(messageHeaderProtocol(requestHeader)).foreach { ct =>
      headers.add(HeaderNames.CONTENT_TYPE, ct)
    }
    val accept = requestHeaderAcceptedResponseProtocols(requestHeader).flatMap { accept =>
      messageProtocolToContentTypeHeader(accept)
    }.mkString(", ")
    if (accept.nonEmpty) {
      headers.add(HeaderNames.ACCEPT, accept)
    }
    messageHeaderHeaders(requestHeader).foreach {
      case (_, values) =>
        values.foreach { value =>
          headers.add(value._1, value._2)
        }
    }

    val channelFuture = client.connect(tgt.getHost, tgt.getPort)
    for {
      _ <- channelFuture.toScala
      channel = channelFuture.channel()
      handshaker = WebSocketClientHandshakerFactory.newHandshaker(tgt, version, null, false, headers)
      _ <- handshaker.handshake(channel).toScala
      incomingPromise = Promise[(ResponseHeader, Source[ByteString, NotUsed])]()
      _ = channel.pipeline().addLast("supervisor", new WebSocketSupervisor(exceptionSerializer, handshaker, outgoing,
        incomingPromise, messageHeaderProtocol(requestHeader)))
      _ = channel.read()
      incoming <- incomingPromise.future
    } yield incoming
  }

  def shutdown(): Future[_] = {
    Future.sequence(channelGroup.close().asScala.map(_.toScala).toSeq)
  }

  private class WebSocketSupervisor(exceptionSerializer: ExceptionSerializer, handshaker: WebSocketClientHandshaker,
                                    outgoing: Source[ByteString, NotUsed], incomingPromise: Promise[(ResponseHeader, Source[ByteString, NotUsed])],
                                    requestProtocol: MessageProtocol) extends ChannelDuplexHandler {

    private val NormalClosure = 1000

    private sealed trait State
    private case object Handshake extends State
    private case object Open extends State
    private case object ClientInitiatedClose extends State
    private case object Closed extends State

    private val outgoingStreamError = new AtomicReference[Throwable]()

    private var state: State = Handshake
    private var responseProtocol: Option[MessageProtocol] = None

    override def channelRead(ctx: ChannelHandlerContext, msg: Object) = {
      msg match {
        case resp: FullHttpResponse if state == Handshake =>
          try {
            val rp = messageProtocolFromContentTypeHeader(Option(resp.headers().get(HeaderNames.CONTENT_TYPE)))
            responseProtocol = Some(rp)
            val headers = resp.headers().asScala.map { header =>
              header.getKey -> header.getValue
            }.groupBy(_._1.toLowerCase(Locale.ENGLISH)).map {
              case (key, values) => key -> values.to[immutable.Seq]
            }

            // See if the response is an error response
            if (resp.getStatus.code >= 400 && resp.getStatus.code < 599) {
              incomingPromise.failure(exceptionSerializerDeserializeHttpException(
                exceptionSerializer,
                resp.getStatus.code, rp, toByteString(resp)
              ))
              ctx.close()

            } else {

              // Setup the pipeline
              val channelPublisher = new HandlerPublisher(ctx.executor, classOf[ByteString]) {
                override def cancelled() = clientInitiatedClose(ctx)
              }
              val channelSubscriber = new HandlerSubscriber[ByteString](ctx.executor) {
                override def error(error: Throwable) = {
                  // Attempt to both send this error to the server, and return it back to the client
                  outgoingStreamError.set(error)

                  val rawExceptionMessage = exceptionSerializerSerialize(exceptionSerializer, error, Nil)
                  clientInitiatedClose(ctx, new CloseWebSocketFrame(
                    rawExceptionMessageWebSocketCode(rawExceptionMessage),
                    rawExceptionMessageMessageAsText(rawExceptionMessage)
                  ))
                }
                override def complete() = clientInitiatedClose(ctx)
              }
              ctx.pipeline.addAfter(ctx.executor, ctx.name, "websocket-subscriber", channelSubscriber)
              ctx.pipeline.addAfter(ctx.executor, ctx.name, "websocket-publisher", channelPublisher)

              try {
                // We *must* be ready to handle websocket frames before we invoke finishHandshake, since it may
                // recursively trigger more reads, so hence we need to set the state to open here.
                // See https://github.com/netty/netty/issues/4533
                // However, we don't connect up to the streams until after we finish the handshake, in case it fails.
                state = Open

                handshaker.finishHandshake(ctx.channel(), resp)
                val clientConnection = Flow.fromSinkAndSource(
                  Sink.fromSubscriber(channelSubscriber),
                  Source.fromPublisher(channelPublisher)
                )
                val incoming = outgoing via clientConnection

                // This flow replaces any upstream signal with any errors caught by the outgoing stream (ie, produced on
                // the client) to the incoming stream, so that the client can handle them appropriately.
                // Note that when outgoingStreamError is set, the client will immediately send a close to the server. In
                // the normal case, this will result in the server then closing the connection, which will eventually
                // close this stream, which is what will trigger that error to be published.
                val injectOutgoingStreamError = Flow[ByteString].transform(() => new PushStage[ByteString, ByteString] {
                  def errorOr[T >: TerminationDirective](ctx: Context[ByteString])(block: => T): T = {
                    val error = outgoingStreamError.get()
                    if (error != null) {
                      ctx.fail(error)
                    } else {
                      block
                    }
                  }
                  override def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = errorOr[SyncDirective](ctx) {
                    ctx.push(elem)
                  }
                  override def onUpstreamFinish(ctx: Context[ByteString]): TerminationDirective = errorOr(ctx) {
                    ctx.finish()
                  }
                  override def onUpstreamFailure(cause: Throwable, ctx: Context[ByteString]): TerminationDirective = {
                    ctx.fail(cause)
                  }
                })
                val responseHeader = newResponseHeader(resp.getStatus.code, rp, headers)
                incomingPromise.success((responseHeader, incoming via injectOutgoingStreamError))
              } catch {
                case NonFatal(e) =>
                  state = Closed
                  ctx.close()
                  incomingPromise.failure(e)
              }
            }

          } finally {
            ReferenceCountUtil.release(resp)
          }

        case unexpectedResponse: FullHttpResponse =>
          ReferenceCountUtil.release(unexpectedResponse)
          ctx.close()
          throw new WebSocketException("Received a second, unexpected HTTP response: " + unexpectedResponse)

        case unexpectedDuringHandshake if state == Handshake =>
          ReferenceCountUtil.release(unexpectedDuringHandshake)
          ctx.close()
          throw new WebSocketException("Unexpected message received during handshake: " + unexpectedDuringHandshake)

        case message: WebSocketFrame if !message.isFinalFragment =>
          ReferenceCountUtil.release(message)
          protocolError(ctx, newPayloadTooLarge("This client does not support fragmented frames"))

        case frame: CloseWebSocketFrame if state == Open =>
          // server initiated close, echo back the frame, remove the handlers from the pipeline
          state = Closed
          if (frame.statusCode() == -1 || frame.statusCode() == NormalClosure) {
            // Just remove the publisher so it terminates
            ctx.pipeline().remove("websocket-publisher")
          } else {
            // The WebSocket closed in error, publish an exception caught message so the publisher publishes the error
            ctx.fireExceptionCaught(exceptionSerializerDeserializeWebSocketException(
              exceptionSerializer,
              frame.statusCode(), requestProtocol, ByteString(frame.reasonText())
            ))
          }
          ctx.writeAndFlush(frame)
          ctx.pipeline().remove("websocket-subscriber")

        case frame: CloseWebSocketFrame =>
          // response to client initiated close, or possibly simultaneous server/client initiated close, response is the
          // same either way, let the server close the connection
          state = Closed
          ReferenceCountUtil.release(frame)
          ctx.pipeline().remove("websocket-subscriber")
          ctx.pipeline().remove("websocket-publisher")

        case ping: PingWebSocketFrame if state == Open =>
          // ping, send pong
          ctx.writeAndFlush(new PongWebSocketFrame(ping.content()))

        case ping: PingWebSocketFrame =>
          // ping while closing, ignore
          ReferenceCountUtil.release(ping)

        case pong: PongWebSocketFrame =>
          // pong, nothing to do
          ReferenceCountUtil.release(pong)

        case (_: TextWebSocketFrame | _: BinaryWebSocketFrame) if state == Open || state == ClientInitiatedClose =>
          // Even if the client has closed, we still want to send it any remaining messages from the sender,
          // since it may be the source that closed, the sink may still want to receive messages.
          val message = msg.asInstanceOf[WebSocketFrame]
          val bytes = toByteString(message)
          ReferenceCountUtil.release(message)
          ctx.fireChannelRead(bytes)

        case _ =>
          ReferenceCountUtil.release(msg)
          protocolError(ctx, newPolicyViolation("UnexpectedMessage", "Unexpected message received from server"))
      }
    }

    override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise) = {
      msg match {
        case bytes: ByteString if state == Open =>
          val frame = if (messageProtocolIsUtf8(requestProtocol)) {
            // If we're speaking UTF-8, we can place the bytes in a TextMessage as is
            new TextWebSocketFrame(Unpooled.copiedBuffer(bytes.asByteBuffers.toArray: _*))
          } else if (messageProtocolIsText(requestProtocol)) {
            // Otherwise, if it's text, we need to decode as a String
            new TextWebSocketFrame(bytes.decodeString(messageProtocolCharset(requestProtocol).getOrElse("utf-8")))
          } else {
            new BinaryWebSocketFrame(Unpooled.copiedBuffer(bytes.asByteBuffers.toArray: _*))
          }
          ctx.write(frame, promise)

        case bytes: ByteString =>
        // We've already sent a close message, we must not send any subsequent messages, so ignore
      }
    }

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
      if (state == Open) {
        // Just forward. When open, handler publisher/subscriber handles back pressure
        ctx.fireChannelReadComplete()
      } else {
        // Otherwise read, we always want to read.
        ctx.read()
      }
    }

    private def toByteString(data: ByteBufHolder) = {
      val builder = ByteString.newBuilder
      data.content().readBytes(builder.asOutputStream, data.content().readableBytes())
      val bytes = builder.result()
      bytes
    }

    private def protocolError(ctx: ChannelHandlerContext, error: Throwable) = {
      // todo accept headers
      val rawExceptionMessage = exceptionSerializerSerialize(exceptionSerializer, error, Nil)
      doClientInitiatedClose(ctx, new CloseWebSocketFrame(
        rawExceptionMessageWebSocketCode(rawExceptionMessage),
        rawExceptionMessageMessageAsText(rawExceptionMessage)
      ))
      // Send the error to the publisher to be published
      ctx.fireExceptionCaught(error)
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: Throwable) = {
      if (!incomingPromise.isCompleted) {
        incomingPromise.failure(e)
      }
      ctx.fireExceptionCaught(e)
      ctx.channel.close()
    }

    override def channelInactive(ctx: ChannelHandlerContext) = {
      if (!incomingPromise.isCompleted) {
        incomingPromise.failure(new IllegalStateException("WebSocket connection closed before handshake complete"))
      }
    }

    private def clientInitiatedClose(ctx: ChannelHandlerContext): Unit = {
      clientInitiatedClose(ctx, new CloseWebSocketFrame(NormalClosure, ""))
    }

    private def clientInitiatedClose(ctx: ChannelHandlerContext, msg: CloseWebSocketFrame): Unit = {
      if (ctx.executor().inEventLoop()) {
        doClientInitiatedClose(ctx, msg)
      } else {
        ctx.executor().execute(new Runnable {
          override def run() = doClientInitiatedClose(ctx, msg)
        })
      }
    }

    private def doClientInitiatedClose(ctx: ChannelHandlerContext, msg: CloseWebSocketFrame) = {
      if (state == Open) {
        ctx.writeAndFlush(msg)
        // Do a read, just in case it's needed to get the close message in response.
        ctx.read()
        state = ClientInitiatedClose
      }
    }
  }

}

object WebSocketClient {
  private[lagom] def createEventLoopGroup(lifecycle: ApplicationLifecycle): EventLoopGroup = {
    val eventLoop = new NioEventLoopGroup()
    lifecycle.addStopHook { () =>
      // The first argument here is a quiet period, between which events will be rejected, and shutdown actually starts.
      // We want no quiet period, if we want to shutdown, we want to shutdown.
      eventLoop.shutdownGracefully(0, 10, TimeUnit.SECONDS).toScala
    }
    eventLoop
  }
}

class WebSocketException(s: String, th: Throwable) extends java.io.IOException(s, th) {
  def this(s: String) = this(s, null)
}
