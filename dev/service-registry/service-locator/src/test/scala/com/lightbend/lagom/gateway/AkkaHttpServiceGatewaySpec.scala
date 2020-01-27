/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.gateway

import java.net.URI

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.transport.Method
import com.lightbend.lagom.registry.impl.ServiceRegistryActor
import com.lightbend.lagom.registry.impl.UnmanagedServices
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.core.server.Server.ServerStoppedReason

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

class AkkaHttpServiceGatewaySpec extends WordSpec with Matchers with BeforeAndAfterAll {
  implicit val actorSystem = ActorSystem()
  import actorSystem.dispatcher
  implicit val mat        = ActorMaterializer()
  val coordinatedShutdown = CoordinatedShutdown(actorSystem)
  val http                = Http()

  var gateway: AkkaHttpServiceGateway = _
  var servicePort: Int                = _

  protected override def beforeAll(): Unit = {
    val serviceBinding = Await.result(
      http.bindAndHandle(
        Flow[HttpRequest].map {
          case hello if hello.uri.path.toString() == "/hello" => HttpResponse(entity = HttpEntity("Hello!"))
          case req if req.uri.path.toString() == "/echo-headers" =>
            HttpResponse(entity = HttpEntity(req.headers.map(h => s"${h.name()}: ${h.value}").mkString("\n")))
          case stream if stream.uri.path.toString() == "/stream" =>
            stream
              .header[UpgradeToWebSocket]
              .get
              .handleMessages(
                Flow[Message],
                stream.headers
                  .find(_.lowercaseName() == "sec-websocket-protocol")
                  .map(_.value)
                  .map(_.split(","))
                  .map(_.head)
              )
        },
        "localhost",
        port = 0
      ),
      10.seconds
    )

    servicePort = serviceBinding.localAddress.getPort

    val serviceRegistry = actorSystem.actorOf(
      Props(
        new ServiceRegistryActor(
          new UnmanagedServices(
            Map(
              "service" -> ServiceRegistryService.of(
                URI.create(s"http://localhost:$servicePort"),
                Seq(
                  ServiceAcl.methodAndPath(Method.GET, "/hello"),
                  ServiceAcl.methodAndPath(Method.GET, "/echo-headers"),
                  ServiceAcl.methodAndPath(Method.GET, "/stream")
                ).asJava
              )
            )
          )
        )
      )
    )

    gateway = new AkkaHttpServiceGateway(coordinatedShutdown, ServiceGatewayConfig("127.0.0.1", 0), serviceRegistry)
  }

  def gatewayUri   = "http://localhost:" + gateway.address.getPort
  def gatewayWsUri = "ws://localhost:" + gateway.address.getPort

  "The Akka HTTP service gateway" should {
    "serve simple requests" in {
      val answer = Await.result(
        for {
          response <- http.singleRequest(HttpRequest(uri = s"$gatewayUri/hello"))
          data     <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
        } yield data.utf8String,
        10.seconds
      )

      answer should ===("Hello!")
    }

    "serve websocket requests" in {
      val flow = http.webSocketClientFlow(WebSocketRequest(s"$gatewayWsUri/stream"))
      val result = Await.result(
        Source(List("Hello", "world"))
          .map(TextMessage(_))
          .via(flow)
          .collect {
            case TextMessage.Strict(text) => text
          }
          .runWith(Sink.seq),
        10.seconds
      )

      (result should contain).inOrderOnly("Hello", "world")
    }

    "serve websocket requests with the correct response" in {
      val flow = http.webSocketClientFlow(
        WebSocketRequest(
          s"$gatewayWsUri/stream",
          collection.immutable.Seq.empty[HttpHeader],
          collection.immutable.Seq("echo")
        )
      )
      val result = Await.result(
        Source
          .single(TextMessage("hello world!"))
          .viaMat(flow)(Keep.right)
          .to(Sink.ignore)
          .run(),
        10.seconds
      )

      result.response.status should equal(StatusCodes.SwitchingProtocols)
      result.response.headers.count(_.lowercaseName() == "sec-websocket-protocol") should equal(1)
      result.response.headers.find(_.lowercaseName() == "sec-websocket-protocol").map(_.value()).get should equal(
        "echo"
      )
    }

    "serve not found when no ACL matches" in {
      val response = Await.result(http.singleRequest(HttpRequest(uri = s"$gatewayUri/notfound")), 10.seconds)
      response.status.intValue() should ===(404)
    }

    "Rewrite 'Host: ' and stack into 'X-Forwarded-Host: ' " in {
      val answer: String = Await.result(
        for {
          response <- http.singleRequest(HttpRequest(uri = s"$gatewayUri/echo-headers"))
          data     <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
        } yield data.utf8String,
        10.seconds
      )

      val port = gateway.address.getPort

      val headers = answer.split("\n")
      headers should contain(s"Host: localhost:$servicePort")

      // The following two assertions should be switched when https://github.com/akka/akka-http/issues/2191 is fixed
      headers should contain(s"X-Forwarded-Host: localhost")
      //      answer should contain(s"X-Forwarded-Host: localhost:$port")
    }
  }

  protected override def afterAll(): Unit = {
    coordinatedShutdown.run(ServerStoppedReason)
  }
}
