/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.it

import akka.pattern.CircuitBreakerOpenException
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.Done
import akka.NotUsed
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.AdditionalConfiguration
import com.lightbend.lagom.scaladsl.it.mocks.MockRequestEntity
import com.lightbend.lagom.scaladsl.it.mocks.MockResponseEntity
import com.lightbend.lagom.scaladsl.it.mocks.MockService
import com.lightbend.lagom.scaladsl.it.mocks.MockServiceImpl
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.api.Configuration
import play.api.libs.streams.AkkaStreams
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ScaladslMockServiceSpec extends WordSpec with Matchers {

  List(AkkaHttp, Netty).foreach { implicit backend =>
    s"A mock service ($backend)" should {
      "be possible to invoke" in withServer { implicit mat => client =>
        val id       = 10L
        val request  = MockRequestEntity("bar", 20)
        val response = Await.result(client.mockCall(id).invoke(request), 10.seconds)
        response.incomingId should ===(id)
        response.incomingRequest should ===(request)
      }
      "be possible to invoke for NotUsed parameters" in withServer { implicit mat => client =>
        MockService.invoked.set(false)
        Await.result(client.doNothing.invoke(), 10.seconds) should ===(NotUsed)
        MockService.invoked.get() should ===(true)
      }
      "be possible to invoke for Done parameters and response" in withServer { implicit mat => client =>
        val response = Await.result(client.doneCall.invoke(Done), 10.seconds)
        response should ===(Done)
      }
      "be possible to invoke for ByteString parameters and response" in withServer { implicit mat => client =>
        val request = ByteString.fromString("raw ByteString")
        Await.result(client.echoByteString.invoke(request), 10.seconds) should ===(request)
      }

      "work with streamed responses" in withServer { implicit mat => client =>
        val request = new MockRequestEntity("entity", 1)
        Try(Await.result(client.streamResponse.invoke(request), 10.seconds)) match {
          case Success(result) =>
            consume(result) should ===((1 to 3).map(i => MockResponseEntity(i, request)))
          case Failure(_) =>
            println(
              "SKIPPED - This may sometimes fail due to https://github.com/playframework/playframework/issues/5365"
            )
        }
      }

      "work with streamed responses and unit requests" in withServer { implicit mat => client =>
        val resultStream = Await.result(client.unitStreamResponse.invoke(), 10.seconds)
        consume(resultStream) should ===((1 to 3).map(i => MockResponseEntity(i, new MockRequestEntity("entity", i))))
      }
      "work with streamed requests" in withServer { implicit mat => client =>
        val requests    = (1 to 3).map(i => new MockRequestEntity("request", i))
        val gotResponse = Promise[None.type]()
        val closeWhenGotResponse =
          Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
        val result =
          Await.result(client.streamRequest.invoke(Source(requests).concat(closeWhenGotResponse)), 10.seconds)
        gotResponse.success(None)
        result should ===(MockResponseEntity(1, requests(0)))
      }
      "work with streamed requests and unit responses" when {
        "an empty message is sent for unit" in withServer { implicit mat => client =>
          // In this case, we wait for a response from the server before closing the connection. The response will be an
          // empty web socket message which will be returned to us as null
          MockService.firstReceived.set(null)
          val requests    = (1 to 3).map(i => new MockRequestEntity("request", i))
          val gotResponse = Promise[None.type]()
          val closeWhenGotResponse =
            Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
          Await.result(client.streamRequestUnit.invoke(Source(requests).concat(closeWhenGotResponse)), 10.seconds) should ===(
            NotUsed
          )
          gotResponse.success(None)
          MockService.firstReceived.get() should ===(requests(0))
        }
        "no message is sent for unit" in withServer { implicit mat => client =>
          // In this case, we send nothing to the server, which is waiting for a single message before it sends a response,
          // instead we just close the connection, we want to make sure that the client call still returns.
          MockService.firstReceived.set(null)
          Await.result(client.streamRequestUnit.invoke(Source.empty), 10.seconds) should ===(NotUsed)
        }
      }
      "work with bidi streams" when {
        "the client closes the connection" in withServer { implicit mat => client =>
          val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
          // Use a source that never terminates so we don't close the upstream (which would close the downstream), and then
          // use takeUpTo so that we close downstream when we've got everything we want
          val resultStream = Await.result(client.bidiStream.invoke(Source(requests).concat(Source.maybe)), 10.seconds)
          consume(resultStream.take(3)) should ===(requests.map(r => MockResponseEntity(1, r)))
        }
        "the server closes the connection" in withServer { implicit mat => client =>
          val requests    = (1 to 3).map(i => new MockRequestEntity("request", i))
          val gotResponse = Promise[None.type]()
          val closeWhenGotResponse =
            Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
          val serverClosed = Promise[Done]()
          val trackServerClosed =
            AkkaStreams.ignoreAfterCancellation[MockResponseEntity].mapMaterializedValue(serverClosed.completeWith)
          val resultStream =
            Await.result(client.bidiStream.invoke(Source(requests).concat(closeWhenGotResponse)), 10.seconds)
          consume(resultStream.via(trackServerClosed).take(3)) should ===(requests.map(r => MockResponseEntity(1, r)))
          gotResponse.success(None)
          Await.result(serverClosed.future, 10.seconds) should ===(Done)
        }
      }
      "work with custom headers" in withServer { implicit mat => client =>
        val (responseHeader, result) = Await.result(
          client.customHeaders
            .handleRequestHeader(_.withHeader("Foo-Header", "Bar"))
            .withResponseHeader
            .invoke("Foo-Header"),
          10.seconds
        )
        result should ===("Bar")
        responseHeader.getHeader("Header-Name") should ===(Some("Foo-Header"))
        responseHeader.status should ===(201)
      }
      "work with custom headers on streams" in withServer { implicit mat => client =>
        val result = Await.result(
          client.streamCustomHeaders
            .handleRequestHeader(_.withHeaders(List("Header-1" -> "value1", "Header-2" -> "value2")))
            .invoke(Source(List("Header-1", "Header-2")).concat(Source.maybe)),
          10.seconds
        )
        val values = consume(result.via(Flow[String].take(2)))
        values should ===(Seq("value1", "value2"))
      }
      "send the service name" in withServer { implicit mat => client =>
        Await.result(client.serviceName.invoke(), 10.seconds) should ===("mockservice")
      }
      "send the service name on streams" in withServer { implicit mat => client =>
        Await.result(
          Await
            .result(client.streamServiceName.invoke(), 10.seconds)
            .runWith(Sink.head),
          10.seconds
        ) should ===("mockservice")
      }

      "work with query params" in withServer { implicit mat => client =>
        Await.result(
          client.queryParamId(Some("foo")).invoke(),
          10.seconds
        ) should ===("foo")
      }

      "work with collections of entities" in withServer { implicit mat => client =>
        val request  = new MockRequestEntity("results", 10)
        val response = Await.result(client.listResults.invoke(request), 10.seconds)

        response.size should ===(request.field2)
      }

      "work with custom serializers" when {
        "the serializer protocol uses a custom contentType" in withServer { implicit mat => client =>
          val id       = 20
          val request  = new MockRequestEntity("bar", id)
          val response = Await.result(client.customContentType.invoke(request), 10.seconds)
          response.incomingId should ===(id)
          response.incomingRequest should ===(request)
        }

        "the serializer protocol does not specify a contentType" in withServer { implicit mat => client =>
          val id       = 20
          val request  = new MockRequestEntity("bar", id)
          val response = Await.result(client.noContentType.invoke(request), 10.seconds)
          response.incomingId should ===(id)
          response.incomingRequest should ===(request)
        }
      }

      "be invoked with circuit breaker" in withServer { implicit mat => client =>
        MockService.invoked.set(false)
        (1 to 20).foreach { _ =>
          intercept[Exception] {
            Await.result(client.alwaysFail.invoke(), 10.seconds)
          }
        }
        MockService.invoked.get() should ===(true)
        MockService.invoked.set(false)
        intercept[CircuitBreakerOpenException] {
          Await.result(client.alwaysFail.invoke(), 10.seconds)
        }
        MockService.invoked.get() should ===(false)
      }

    }
  }

  private def consume[A](source: Source[A, _])(implicit mat: Materializer): Seq[A] = {
    Await.result(source.runWith(Sink.seq), 10.seconds)
  }

  private def withServer(block: Materializer => MockService => Unit)(implicit httpBackend: HttpBackend): Unit = {

    ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
      new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents with LocalServiceLocator {
        override lazy val lagomServer = serverFor[MockService](new MockServiceImpl)

        override def additionalConfiguration: AdditionalConfiguration = {
          import scala.collection.JavaConverters._
          super.additionalConfiguration ++ ConfigFactory.parseMap(
            Map(
              "play.server.provider" -> httpBackend.provider
            ).asJava
          )
        }
      }
    } { server =>
      block(server.materializer)(server.serviceClient.implement[MockService])
    }

  }
}
