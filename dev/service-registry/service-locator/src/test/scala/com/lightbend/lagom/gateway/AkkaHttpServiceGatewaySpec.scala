/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.gateway

import java.net.URI

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{ Message, TextMessage, UpgradeToWebSocket, WebSocketRequest }
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, Uri }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.ByteString
import com.lightbend.lagom.discovery.{ ServiceRegistryActor, UnmanagedServices }
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.transport.Method
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import play.api.inject.DefaultApplicationLifecycle

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.JavaConverters._

class AkkaHttpServiceGatewaySpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val actorSystem = ActorSystem()
  import actorSystem.dispatcher
  implicit val mat = ActorMaterializer()
  val http = Http()

  var serviceBinding: Http.ServerBinding = _
  var gateway: AkkaHttpServiceGateway = _

  override protected def beforeAll(): Unit = {
    serviceBinding = Await.result(http.bindAndHandle(Flow[HttpRequest].map {
      case hello if hello.uri.path.toString() == "/hello" => HttpResponse(entity = HttpEntity("Hello!"))
      case stream if stream.uri.path.toString() == "/stream" =>
        stream.header[UpgradeToWebSocket].get.handleMessages(Flow[Message])
    }, "localhost", port = 0), 10.seconds)

    val serviceRegistry = actorSystem.actorOf(Props(new ServiceRegistryActor(new UnmanagedServices(
      Map("service" -> new ServiceRegistryService(
        URI.create(s"http://localhost:${serviceBinding.localAddress.getPort}"),
        Seq(
          ServiceAcl.methodAndPath(Method.GET, "/hello"),
          ServiceAcl.methodAndPath(Method.GET, "/stream")
        ).asJava
      ))
    ))))

    gateway = new AkkaHttpServiceGateway(new DefaultApplicationLifecycle, ServiceGatewayConfig(0), serviceRegistry)
  }

  def gatewayUri = "http://localhost:" + gateway.address.getPort
  def gatewayWsUri = "ws://localhost:" + gateway.address.getPort

  "The Akka HTTP service gateway" should {

    "serve simple requests" in {
      val answer = Await.result(for {
        response <- http.singleRequest(HttpRequest(uri = s"$gatewayUri/hello"))
        data <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
      } yield data.utf8String, 10.seconds)

      answer should ===("Hello!")
    }

    "serve websocket requests" in {
      val flow = http.webSocketClientFlow(WebSocketRequest(s"$gatewayWsUri/stream"))
      val result = Await.result(Source(List("Hello", "world")).map(TextMessage(_)).via(flow).collect {
        case TextMessage.Strict(text) => text
      }.runWith(Sink.seq), 10.seconds)

      result should contain inOrderOnly ("Hello", "world")
    }

    "serve not found when no ACL matches" in {
      val response = Await.result(http.singleRequest(HttpRequest(uri = s"$gatewayUri/notfound")), 10.seconds)
      response.status.intValue() should ===(404)
    }

  }

  override protected def afterAll(): Unit = {
    actorSystem.terminate()
  }
}
