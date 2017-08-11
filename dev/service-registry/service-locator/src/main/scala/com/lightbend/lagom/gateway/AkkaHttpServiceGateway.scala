/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.gateway

import java.net.InetSocketAddress
import java.util.Locale
import javax.inject.{ Inject, Named }

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.util.Timeout
import com.lightbend.lagom.discovery.ServiceRegistryActor.{ Found, NotFound, Route, RouteResult }
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import org.slf4j.LoggerFactory
import play.api.inject.ApplicationLifecycle
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter

import scala.collection.immutable
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class AkkaHttpServiceGatewayFactory @Inject() (lifecycle: ApplicationLifecycle, config: ServiceGatewayConfig,
                                               @Named("serviceRegistryActor") registry: ActorRef)(implicit actorSystem: ActorSystem, mat: Materializer) {

  def start(): InetSocketAddress = {
    new AkkaHttpServiceGateway(lifecycle, config, registry).address
  }
}

class AkkaHttpServiceGateway(lifecycle: ApplicationLifecycle, config: ServiceGatewayConfig, registry: ActorRef)(implicit actorSystem: ActorSystem, mat: Materializer) {

  private val log = LoggerFactory.getLogger(classOf[AkkaHttpServiceGateway])

  import actorSystem.dispatcher
  private implicit val timeout = Timeout(5.seconds)
  val http = Http()

  private val handler = Flow[HttpRequest].mapAsync(1) { request =>
    log.debug("Routing request {}", request)
    val path = request.uri.path.toString()

    (registry ? Route(request.method.name, path)).mapTo[RouteResult].flatMap {
      case Found(serviceAddress) =>
        log.debug("Request is to be routed to {}", serviceAddress)
        val newUri = request.uri.withAuthority(serviceAddress.getHostName, serviceAddress.getPort)
        request.header[UpgradeToWebSocket] match {
          case Some(upgrade) =>
            handleWebSocketRequest(request, newUri, upgrade)
          case None =>
            http.singleRequest(request.withUri(newUri).withHeaders(filterHeaders(request.headers)))
        }
      case NotFound(registryMap) =>
        log.debug("Sending not found response")
        Future.successful(renderNotFound(request, path, registryMap))
    }
  }

  private def handleWebSocketRequest(request: HttpRequest, uri: Uri, upgrade: UpgradeToWebSocket) = {
    log.debug("Switching to WebSocket protocol")
    val wsUri = uri.withScheme("ws")
    val flow = Flow.fromSinkAndSourceMat(Sink.asPublisher[Message](fanout = false), Source.asSubscriber[Message])(Keep.both)

    val (responseFuture, (publisher, subscriber)) = http.singleWebSocketRequest(
      WebSocketRequest(wsUri, extraHeaders = filterHeaders(request.headers),
        upgrade.requestedProtocols.headOption),
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

  private def renderNotFound(request: HttpRequest, path: String, registry: Map[String, ServiceRegistryService]): HttpResponse = {
    import scala.collection.JavaConverters._
    import scala.compat.java8.OptionConverters._
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

    val html = views.html.defaultpages.devNotFound(request.method.name, path, Some(router)).body
    HttpResponse(
      status = StatusCodes.NotFound,
      entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        html
      )
    )
  }

  private val HeadersToFilter = Set(
    "Timeout-Access",
    "Sec-WebSocket-Accept",
    "Sec-WebSocket-Version",
    "Sec-WebSocket-Key",
    "UpgradeToWebSocket",
    "Upgrade",
    "Connection"
  ).map(_.toLowerCase(Locale.ENGLISH))

  private def filterHeaders(headers: immutable.Seq[HttpHeader]) = {
    headers.filterNot(header => HeadersToFilter(header.lowercaseName()))
  }

  private val bindingFuture = Http().bindAndHandle(handler, "0.0.0.0", config.port)
  lifecycle.addStopHook(() => {
    for {
      binding <- bindingFuture
      unbind <- binding.unbind()
    } yield unbind
  })

  val address: InetSocketAddress = Await.result(bindingFuture, 10.seconds).localAddress
}
