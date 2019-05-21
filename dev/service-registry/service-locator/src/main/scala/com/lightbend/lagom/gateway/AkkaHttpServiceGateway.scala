/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.gateway

import java.net.InetSocketAddress
import java.util.Locale

import akka.Done
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.headers.`X-Forwarded-Host`
import akka.http.scaladsl.model.ws._
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import com.lightbend.lagom.registry.impl.ServiceRegistryActor.Found
import com.lightbend.lagom.registry.impl.ServiceRegistryActor.NotFound
import com.lightbend.lagom.registry.impl.ServiceRegistryActor.Route
import com.lightbend.lagom.registry.impl.ServiceRegistryActor.RouteResult
import javax.inject.Inject
import javax.inject.Named
import org.slf4j.LoggerFactory
import play.api.libs.typedmap.TypedMap
import play.api.mvc.request.RemoteConnection
import play.api.mvc.request.RequestAttrKey
import play.api.mvc.request.RequestTarget
import play.api.mvc.Headers
import play.api.mvc.RequestHeader
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future

class AkkaHttpServiceGatewayFactory @Inject()(coordinatedShutdown: CoordinatedShutdown, config: ServiceGatewayConfig)(
    @Named("serviceRegistryActor") registry: ActorRef
)(implicit actorSystem: ActorSystem, mat: Materializer) {

  def start(): InetSocketAddress = {
    new AkkaHttpServiceGateway(coordinatedShutdown, config, registry).address
  }
}

class AkkaHttpServiceGateway(
    coordinatedShutdown: CoordinatedShutdown,
    config: ServiceGatewayConfig,
    registry: ActorRef
)(implicit actorSystem: ActorSystem, mat: Materializer) {

  private val log = LoggerFactory.getLogger(classOf[AkkaHttpServiceGateway])

  import actorSystem.dispatcher
  private implicit val timeout = Timeout(5.seconds)
  val http                     = Http()

  private val handler = Flow[HttpRequest].mapAsync(1) { request =>
    log.debug("Routing request {}", request)

    val path = request.uri.path.toString()
    (registry ? Route(request.method.name, path, None)).mapTo[RouteResult].flatMap {
      case Found(serviceUri) =>
        log.debug("Request is to be routed to {}", serviceUri)
        val newUri = request.uri.withAuthority(serviceUri.getHost, serviceUri.getPort)
        request.header[UpgradeToWebSocket] match {
          case Some(upgrade) =>
            handleWebSocketRequest(request, newUri, upgrade)
          case None =>
            val xForwardedHost = request.header[Host].to[Set].map { h =>
              `X-Forwarded-Host`(h.host)
            }
            val newHostHeader = Set(Host(newUri.authority))
            val headers =
              filterHeaders(request.headers) ++
                xForwardedHost ++
                newHostHeader

            val outgoingRequest = request
              .withUri(newUri)
              .withHeaders(headers)
            http.singleRequest(outgoingRequest)
        }
      case NotFound(registryMap) =>
        log.debug("Sending not found response")
        Future.successful(renderNotFound(request, path, registryMap.mapValues(_.serviceRegistryService).toMap))
    }

  }

  private def handleWebSocketRequest(request: HttpRequest, uri: Uri, upgrade: UpgradeToWebSocket) = {
    log.debug("Switching to WebSocket protocol")
    val wsUri = uri.withScheme("ws")
    val flow =
      Flow.fromSinkAndSourceMat(Sink.asPublisher[Message](fanout = false), Source.asSubscriber[Message])(Keep.both)

    val (responseFuture, (publisher, subscriber)) = http.singleWebSocketRequest(
      WebSocketRequest(wsUri, extraHeaders = filterHeaders(request.headers), upgrade.requestedProtocols.headOption),
      flow
    )

    responseFuture.map {

      case ValidUpgrade(response, chosenSubprotocol) =>
        val webSocketResponse = upgrade.handleMessages(
          Flow.fromSinkAndSource(Sink.fromSubscriber(subscriber), Source.fromPublisher(publisher)),
          chosenSubprotocol
        )
        webSocketResponse.withHeaders(webSocketResponse.headers ++ filterHeaders(response.headers))

      case InvalidUpgradeResponse(response, cause) =>
        log.debug("WebSocket upgrade response was invalid: {}", cause)
        response
    }
  }

  private def renderNotFound(
      request: HttpRequest,
      path: String,
      registry: Map[String, ServiceRegistryService]
  ): HttpResponse = {
    import scala.collection.JavaConverters._
    import scala.compat.java8.OptionConverters._
    // We're reusing Play's not found error page here, which lists the routes, we need to convert the service registry
    // to a Play router with all the acls in the documentation variable so that it can render it
    val router = new SimpleRouter {
      override def routes: Routes = PartialFunction.empty
      override val documentation: Seq[(String, String, String)] = registry.toSeq.flatMap {
        case (serviceName, service) =>
          val call = s"Service: $serviceName (${service.uris})"
          service.acls().asScala.map { acl =>
            val method = acl.method.asScala.fold("*")(_.name)
            val path   = acl.pathRegex.orElse(".*")
            (method, path, call)
          }
      }
    }

    implicit val requestHeader = createRequestHeader(request)

    val html = views.html.defaultpages.devNotFound(request.method.name, path, Some(router)).body
    HttpResponse(
      status = StatusCodes.NotFound,
      entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        html
      )
    )
  }

  /* As of Play 2.7.x, the default pages templates require a implicit RequestHeader.
   * The RequestHeader is only required because down the road the templates may make user of
   * CSFNonce header (if available).
   *
   * This is not relevant for the gateway, but we need to fabricate a RequestHeader to make it compile.
   * We don't need to fill all fields, but we do our best to fill what can be filled with the data we have at hand.
   */
  private def createRequestHeader(request: HttpRequest): RequestHeader = {
    new RequestHeader {
      override def connection: RemoteConnection = ???
      override def method: String               = request.method.name()
      override def target: RequestTarget        = ???
      override def version: String              = request.protocol.value
      override def headers: Headers             = new AkkaHeadersWrapper(request.headers)
      override def attrs: TypedMap              = TypedMap(RequestAttrKey.Server -> "akka-http")
    }
  }

  private class AkkaHeadersWrapper(akkaHeaders: Seq[HttpHeader])
      extends Headers(akkaHeaders.map(h => (h.name, h.value)))

  private val HeadersToFilter = Set(
    "Timeout-Access",
    "Sec-WebSocket-Accept",
    "Sec-WebSocket-Version",
    "Sec-WebSocket-Key",
    "UpgradeToWebSocket",
    "Upgrade",
    "Connection",
    "Host"
  ).map(_.toLowerCase(Locale.ENGLISH))

  private def filterHeaders(headers: immutable.Seq[HttpHeader]) = {
    headers.filterNot(header => HeadersToFilter(header.lowercaseName()))
  }

  private val bindingFuture = Http().bindAndHandle(handler, config.host, config.port)

  coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, "unbind-akka-http-service-gateway") { () =>
    for {
      binding <- bindingFuture
      _       <- binding.unbind()
    } yield Done
  }

  val address: InetSocketAddress = Await.result(bindingFuture, 10.seconds).localAddress
}
