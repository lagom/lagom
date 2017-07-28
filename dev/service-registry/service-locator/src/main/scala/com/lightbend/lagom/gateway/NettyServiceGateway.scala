/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.gateway

import java.net.{ InetSocketAddress, URI }
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Named, Singleton }

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.lightbend.lagom.internal.NettyFutureConverters
import NettyFutureConverters._
import com.lightbend.lagom.discovery.ServiceRegistryActor.{ Found, NotFound, Route, RouteResult }
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import io.netty.bootstrap.{ Bootstrap, ServerBootstrap }
import io.netty.buffer.{ ByteBuf, Unpooled }
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.pool.{ AbstractChannelPoolHandler, AbstractChannelPoolMap, ChannelPool, SimpleChannelPool }
import io.netty.channel.socket.SocketChannel
import io.netty.channel._
import io.netty.channel.socket.nio.{ NioServerSocketChannel, NioSocketChannel }
import io.netty.handler.codec.http._
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.EventExecutor
import org.slf4j.LoggerFactory
import play.api.{ PlayException, UsefulException }
import play.api.inject.ApplicationLifecycle
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter

import scala.language.implicitConversions
import scala.collection.JavaConverters._
import scala.collection.immutable.Queue
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.compat.java8.OptionConverters._
import scala.util.{ Failure, Success }

import com.lightbend.lagom.internal.api.Execution.trampoline

case class ServiceGatewayConfig(
  port: Int
)

@Singleton
class NettyServiceGatewayFactory @Inject() (lifecycle: ApplicationLifecycle, config: ServiceGatewayConfig,
                                            @Named("serviceRegistryActor") registry: ActorRef) {
  def start(): NettyServiceGateway = {
    new NettyServiceGateway(lifecycle, config, registry)
  }
}

/**
 * Netty implementation of the service gateway.
 */
class NettyServiceGateway(lifecycle: ApplicationLifecycle, config: ServiceGatewayConfig, registry: ActorRef) {

  private implicit val timeout = Timeout(5.seconds)
  private val log = LoggerFactory.getLogger(classOf[NettyServiceGateway])
  private val eventLoop = new NioEventLoopGroup

  private val server = new ServerBootstrap()
    .group(eventLoop)
    .channel(classOf[NioServerSocketChannel])
    .childHandler(new ChannelInitializer[SocketChannel] {
      override def initChannel(channel: SocketChannel) = {
        channel.pipeline.addLast(
          new HttpRequestDecoder,
          new HttpResponseEncoder,
          new ProxyHandler(channel)
        )
      }
    })

  private def client = new Bootstrap()
    .group(eventLoop)
    .channel(classOf[NioSocketChannel])

  private val poolMap = new AbstractChannelPoolMap[InetSocketAddress, SimpleChannelPool] {
    override def newPool(key: InetSocketAddress) = {
      new SimpleChannelPool(client.remoteAddress(key), new AbstractChannelPoolHandler {
        override def channelCreated(ch: Channel) = {
          ch.pipeline().addLast(new HttpResponseDecoder, new HttpRequestEncoder)
        }
      })
    }
  }

  private implicit def ecFromNettyExecutor(executor: EventExecutor): ExecutionContext = new ExecutionContext {
    override def reportFailure(cause: Throwable): Unit = {
      log.error("Error caught in Netty executor", cause)
    }
    override def execute(runnable: Runnable): Unit = executor.execute(runnable)
  }

  private class ProxyHandler(serverChannel: Channel) extends ChannelInboundHandlerAdapter {
    var context: ChannelHandlerContext = null
    var pipeline = Queue.empty[Any]
    var currentPool: ChannelPool = null
    var currentChannel: Channel = null
    var currentRequest: HttpRequest = null
    var websocket = false
    var closeClientChannel = false

    override def handlerAdded(ctx: ChannelHandlerContext) = context = ctx

    override def channelRead(ctx: ChannelHandlerContext, msg: Any) = {
      msg match {
        case request: HttpRequest =>
          if (currentRequest == null) {
            log.debug("Routing request {}", request)
            currentRequest = request
            val path = URI.create(request.uri).getPath

            implicit val ec = ctx.executor
            (registry ? Route(request.method.name, path)).mapTo[RouteResult].map {
              case Found(serviceAddress) =>
                log.debug("Request is to be routed to {}, getting connection from pool", serviceAddress)
                val pool = poolMap.get(serviceAddress)
                currentPool = pool
                currentPool.acquire().toScala.onComplete {
                  case Success(channel) =>
                    log.debug("Received connection from pool for {}", serviceAddress)
                    // Connection was closed before we got a channel to talk to, release it immediately
                    if (currentPool == null) {
                      log.debug("Connection closed before channel received from pool, releasing channel")
                      pool.release(channel)
                    } else {
                      channel.pipeline().addLast(ctx.executor, clientHandler)
                      currentChannel = channel

                      val proxyRequest = prepareProxyRequest(request)

                      channel.writeAndFlush(proxyRequest)
                      flushPipeline()
                    }
                  case Failure(e) =>
                    log.debug("Unable to get connection to service")
                    ReferenceCountUtil.release(currentRequest)
                    currentRequest = null
                    ctx.writeAndFlush(renderError(request, new PlayException(
                      "Bad gateway",
                      "The gateway could not establish a connection to the service"
                    ), HttpResponseStatus.BAD_GATEWAY))
                    flushPipeline()
                }
              case NotFound(registryMap) =>
                log.debug("Sending not found response")
                ReferenceCountUtil.release(currentRequest)
                currentRequest = null
                ctx.writeAndFlush(renderNotFound(request, path, registryMap))
                flushPipeline()
            }.recover {
              case t =>
                ReferenceCountUtil.release(currentRequest)
                currentRequest = null
                log.error("Error routing request", t)
                val response = new DefaultFullHttpResponse(request.protocolVersion, HttpResponseStatus.INTERNAL_SERVER_ERROR)
                HttpUtil.setContentLength(response, 0)
                ctx.writeAndFlush(response)
            }

          } else {
            log.debug("Enqueuing pipelined request")
            // Put the request in the pipeline, it's a pipelined request. Putting it in the pipeline will ensure the
            // pipeline is now non empty, and so any subsequent content received will go into the pipeline behind it.
            pipeline = pipeline.enqueue(request)
          }

        case ignore: HttpContent if currentRequest == null =>
          // We're ignoring the body, because no route was found
          ReferenceCountUtil.release(ignore)
        case content: HttpContent if currentChannel == null || pipeline.nonEmpty =>
          log.debug("Enqueuing pipelined content")
          // If there's no channel, then we're still waiting to get a channel from the pool for the request. Or, if
          // there's something in the pipeline, it means this content is for a pipelined request. So enqueue it into
          // the pipeline.
          pipeline = pipeline.enqueue(content)
        case content: HttpContent =>
          log.debug("Forwarding request content")
          // Otherwise, write to the current channel.
          currentChannel.writeAndFlush(content)

        case byteBuf: ByteBuf if websocket =>
          log.debug("Forwarding client WebSocket data")
          currentChannel.writeAndFlush(byteBuf)
      }
    }

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
      log.debug("Client closed connection, cleaning up")
      // If the channel goes inactive when we have a current channel, close that channel and release it
      if (currentPool != null && currentChannel != null) {
        currentChannel.close()
        currentPool.release(currentChannel)
      }
      pipeline.foreach(ReferenceCountUtil.release)
      pipeline = Queue.empty
      currentChannel = null
      currentPool = null
      currentRequest = null
    }

    private def prepareProxyRequest(request: HttpRequest): HttpRequest = {
      val newRequest = request match {
        case full: FullHttpRequest =>
          new DefaultFullHttpRequest(request.protocolVersion, request.method, request.uri, full.content())
        case _ =>
          new DefaultHttpRequest(request.protocolVersion, request.method, request.uri)
      }
      for (header <- request.headers.asScala) {
        // Ignore connection headers
        if (!header.getKey.equalsIgnoreCase(HttpHeaders.Names.CONNECTION)) {
          newRequest.headers.add(header.getKey, header.getValue)
        }
      }
      // todo - add Forwarded and other headers

      // Ensure the correct connection header is added to keep the connection alive
      HttpUtil.setKeepAlive(newRequest, true)

      newRequest
    }

    /**
     * Flushes the pipeline. This sets the pipeline to empty, and then replays each message to channelRead.
     */
    private def flushPipeline(): Unit = {
      val flushed = pipeline
      pipeline = Queue.empty[Any]
      flushed.foreach(channelRead(context, _))
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      log.warn("Exception caught in client connection", cause)
    }

    private def clientHandler: ChannelHandler = new ChannelInboundHandlerAdapter {
      override def channelRead(ctx: ChannelHandlerContext, msg: Any) = {
        msg match {
          case response: FullHttpResponse =>
            handleResponse(ctx, response)
            handleLastContent(ctx, response)

          case response: HttpResponse =>
            handleResponse(ctx, response)

          case last: LastHttpContent =>
            handleLastContent(ctx, last)

          // Non end content of response
          case content: HttpContent =>
            log.debug("Forwarding response content")
            serverChannel.writeAndFlush(content)

          case byteBuf: ByteBuf if websocket =>
            log.debug("Forwarding server WebSocket data")
            serverChannel.writeAndFlush(byteBuf)
        }
      }

      private def handleResponse(ctx: ChannelHandlerContext, response: HttpResponse) = {
        log.debug("Handling response {}", response)
        // First check if it's a WebSocket response
        if (response.status == HttpResponseStatus.SWITCHING_PROTOCOLS) {
          if (response.headers().get(HttpHeaderNames.UPGRADE) == "websocket") {
            log.debug("Switching to WebSocket protocol")
            websocket = true
            // Remove the HTTP decoder from the server channel and the encoder from the client channel
            context.pipeline.remove(classOf[HttpRequestDecoder])
            ctx.pipeline.remove(classOf[HttpRequestEncoder])
            // Write the response as is
            serverChannel.writeAndFlush(response)
          } else {
            // todo: Send error because we don't support whatever protocol this is
          }
        } else {
          val proxyResponse = prepareProxyResponse(response)
          if (!HttpUtil.isKeepAlive(response)) {
            closeClientChannel = true
          }
          serverChannel.writeAndFlush(proxyResponse)
        }
      }

      private def handleLastContent(ctx: ChannelHandlerContext, last: LastHttpContent) = {
        log.debug("Last content received, cleaning up")
        serverChannel.writeAndFlush(last)

        if (websocket) {
          // Now remove the response decoder/encoder
          ctx.pipeline.remove(classOf[HttpResponseDecoder])
          context.pipeline.remove(classOf[HttpResponseEncoder])
        } else {
          // First handle server channel close if necessary
          if (!HttpUtil.isKeepAlive(currentRequest)) {
            serverChannel.close()
          }

          // Now close client channel if necessary
          if (closeClientChannel) {
            currentChannel.close()
            closeClientChannel = false
          }

          // Now clean up and release
          ctx.pipeline.remove(ctx.handler())
          currentPool.release(currentChannel)
          currentChannel = null
          currentPool = null
          currentRequest = null

          flushPipeline()
        }
      }

      override def channelInactive(ctx: ChannelHandlerContext) = {
        log.debug("Proxy connection closed")
        // This is an out of band channel inactive, our only option is to clean up and close the server channel
        if (currentPool != null && currentChannel != null) {
          currentPool.release(currentChannel)
        }
        pipeline.foreach(ReferenceCountUtil.release)
        pipeline = Queue.empty
        currentChannel = null
        currentPool = null
        currentRequest = null
        serverChannel.close()
      }

      override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
        log.warn("Exception caught in proxy connection", cause)
      }
    }

    private def prepareProxyResponse(response: HttpResponse): HttpResponse = {
      val newResponse = response match {
        case full: FullHttpResponse =>
          new DefaultFullHttpResponse(full.protocolVersion, full.status, full.content())
        case _ =>
          new DefaultHttpResponse(response.protocolVersion, response.status)
      }

      for (header <- response.headers.asScala) {
        // Ignore connection headers
        if (!header.getKey.equalsIgnoreCase(HttpHeaders.Names.CONNECTION)) {
          newResponse.headers.add(header.getKey, header.getValue)
        }
      }

      HttpUtil.setKeepAlive(newResponse, HttpUtil.isKeepAlive(currentRequest))

      newResponse
    }
  }

  private val bindFuture = server.bind(config.port).channelFutureToScala
  lifecycle.addStopHook(() => {
    for {
      channel <- bindFuture
      closed <- channel.close().channelFutureToScala
    } yield {
      eventLoop.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS)
      poolMap.asScala.foreach(_.getValue.close())
    }
  })

  val address: InetSocketAddress = {
    val address = Await.result(bindFuture, 10.seconds).localAddress().asInstanceOf[InetSocketAddress]
    if (address == null) throw new IllegalStateException(s"Channel could not be bound on port ${config.port}")
    address
  }

  private def renderNotFound(request: HttpRequest, path: String, registry: Map[String, ServiceRegistryService]): HttpResponse = {
    // We're reusing Play's not found error page here, which lists the routes, we need to convert the service registry
    // to a Play router with all the acls in the documentation variable so that it can render it
    val router = new SimpleRouter {
      override def routes: Routes = PartialFunction.empty
      override val documentation: Seq[(String, String, String)] = registry.toSeq.flatMap {
        case (serviceName, service) =>
          val call = s"Service: $serviceName (${service.uri})"
          service.acls().asScala.map { acl =>
            val method = acl.method.asScala.fold("*")(_.name)
            val path = acl.pathRegex.orElse(".*")
            (method, path, call)
          }
      }
    }

    val html = views.html.defaultpages.devNotFound(request.method.name, path, Some(router)).body.getBytes("utf-8")
    val response = new DefaultFullHttpResponse(request.protocolVersion, HttpResponseStatus.NOT_FOUND,
      Unpooled.wrappedBuffer(html))
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf8")
    response.headers().set(HttpHeaderNames.DATE, new Date())
    HttpUtil.setContentLength(response, html.length)

    response
  }

  private def renderError(request: HttpRequest, exception: UsefulException, status: HttpResponseStatus): HttpResponse = {
    val html = views.html.defaultpages.devError(None, exception).body.getBytes("utf-8")
    val response = new DefaultFullHttpResponse(request.protocolVersion, status,
      Unpooled.wrappedBuffer(html))
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf8")
    response.headers().set(HttpHeaderNames.DATE, new Date())
    HttpUtil.setContentLength(response, html.length)
    response
  }

}
