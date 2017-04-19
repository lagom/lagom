/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.it

import akka.pattern.CircuitBreakerOpenException
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.{ Done, NotUsed }
import com.lightbend.lagom.scaladsl.it.mocks.{ MockRequestEntity, MockResponseEntity, MockService, MockServiceImpl }
import com.lightbend.lagom.scaladsl.server.{ LagomApplication, LagomApplicationContext, LocalServiceLocator }
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import play.api.libs.streams.AkkaStreams
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class ScaladslMockServiceSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  val server = ServiceTest.startServer(ServiceTest.defaultSetup) { ctx =>
    new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents with LocalServiceLocator {
      override lazy val lagomServer = serverFor[MockService](new MockServiceImpl)
    }
  }

  val client = server.serviceClient.implement[MockService]
  import server.materializer

  "A mock service" should {
    "be possible to invoke" in {
      val id = 10L
      val request = MockRequestEntity("bar", 20)
      val response = Await.result(client.mockCall(id).invoke(request), 10.seconds)
      response.incomingId should ===(id)
      response.incomingRequest should ===(request)
    }
    "be possible to invoke for NotUsed parameters" in {
      MockService.invoked.set(false)
      Await.result(client.doNothing.invoke(), 10.seconds) should ===(NotUsed)
      MockService.invoked.get() should ===(true)
    }
    "be possible to invoke for Done parameters and resonse" in {
      val response = Await.result(client.doneCall.invoke(Done), 10.seconds)
      response should ===(Done)
    }

    "work with streamed responses" in {
      val request = new MockRequestEntity("entity", 1)
      Try(Await.result(client.streamResponse.invoke(request), 10.seconds)) match {
        case Success(result) =>
          consume(result) should ===((1 to 3).map(i => MockResponseEntity(i, request)))
        case Failure(_) =>
          println("SKIPPED - This may sometimes fail due to https://github.com/playframework/playframework/issues/5365")
      }
    }

    "work with streamed responses and unit requests" in {
      val resultStream = Await.result(client.unitStreamResponse.invoke(), 10.seconds)
      consume(resultStream) should ===((1 to 3).map(i => MockResponseEntity(i, new MockRequestEntity("entity", i))))
    }
    "work with streamed requests" in {
      val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
      val gotResponse = Promise[None.type]()
      val closeWhenGotResponse = Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
      val result = Await.result(client.streamRequest.invoke(Source(requests).concat(closeWhenGotResponse)), 10.seconds)
      gotResponse.success(None)
      result should ===(MockResponseEntity(1, requests(0)))
    }
    "work with streamed requests and unit responses" when {
      "an empty message is sent for unit" in {
        // In this case, we wait for a response from the server before closing the connection. The response will be an
        // empty web socket message which will be returned to us as null
        MockService.firstReceived.set(null)
        val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
        val gotResponse = Promise[None.type]()
        val closeWhenGotResponse = Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
        Await.result(client.streamRequestUnit.invoke(Source(requests).concat(closeWhenGotResponse)), 10.seconds) should ===(NotUsed)
        gotResponse.success(None)
        MockService.firstReceived.get() should ===(requests(0))
      }
      "no message is sent for unit" in {
        // In this case, we send nothing to the server, which is waiting for a single message before it sends a response,
        // instead we just close the connection, we want to make sure that the client call still returns.
        MockService.firstReceived.set(null)
        Await.result(client.streamRequestUnit.invoke(Source.empty), 10.seconds) should ===(NotUsed)
      }
    }
    "work with bidi streams" when {
      "the client closes the connection" in {
        val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
        // Use a source that never terminates so we don't close the upstream (which would close the downstream), and then
        // use takeUpTo so that we close downstream when we've got everything we want
        val resultStream = Await.result(client.bidiStream.invoke(Source(requests).concat(Source.maybe)), 10.seconds)
        consume(resultStream.take(3)) should ===(requests.map(r => MockResponseEntity(1, r)))
      }
      "the server closes the connection" in {
        val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
        val gotResponse = Promise[None.type]()
        val closeWhenGotResponse = Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
        val serverClosed = Promise[Done]()
        val trackServerClosed = AkkaStreams.ignoreAfterCancellation[MockResponseEntity].mapMaterializedValue(serverClosed.completeWith)
        val resultStream = Await.result(client.bidiStream.invoke(Source(requests).concat(closeWhenGotResponse)), 10.seconds)
        consume(resultStream via trackServerClosed take 3) should ===(requests.map(r => MockResponseEntity(1, r)))
        gotResponse.success(None)
        Await.result(serverClosed.future, 10.seconds) should ===(Done)
      }
    }
    "work with custom headers" in {
      val (responseHeader, result) = Await.result(client.customHeaders
        .handleRequestHeader(_.withHeader("Foo-Header", "Bar"))
        .withResponseHeader
        .invoke("Foo-Header"), 10.seconds)
      result should ===("Bar")
      responseHeader.getHeader("Header-Name") should ===(Some("Foo-Header"))
      responseHeader.status should ===(201)
    }
    "work with custom headers on streams" in {
      val result = Await.result(client.streamCustomHeaders
        .handleRequestHeader(_.withHeaders(List("Header-1" -> "value1", "Header-2" -> "value2")))
        .invoke(Source(List("Header-1", "Header-2")).concat(Source.maybe)), 10.seconds)
      val values = consume(result.via(Flow[String].take(2)))
      values should ===(Seq("value1", "value2"))
    }
    "send the service name" in {
      Await.result(client.serviceName.invoke(), 10.seconds) should ===("mockservice")
    }
    "send the service name on streams" in {
      Await.result(Await.result(client.streamServiceName.invoke(), 10.seconds)
        .runWith(Sink.head), 10.seconds) should ===("mockservice")
    }

    "work with query params" in {
      Await.result(
        client.queryParamId(Some("foo")).invoke(),
        10.seconds
      ) should ===("foo")
    }

    "work with collections of entities" in {
      val request = new MockRequestEntity("results", 10)
      val response = Await.result(client.listResults.invoke(request), 10.seconds)

      response.size should ===(request.field2)
    }

    "work with custom serializers" when {
      "the serializer protocol uses a custom contentType" in {
        val id = 20
        val request = new MockRequestEntity("bar", id)
        val response = Await.result(client.customContentType.invoke(request), 10.seconds)
        response.incomingId should ===(id)
        response.incomingRequest should ===(request)
      }

      "the serializer protocol does not specify a contentType" in {
        val id = 20
        val request = new MockRequestEntity("bar", id)
        val response = Await.result(client.noContentType.invoke(request), 10.seconds)
        response.incomingId should ===(id)
        response.incomingRequest should ===(request)
      }
    }

    "be invoked with circuit breaker" in {
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

  private def consume[A](source: Source[A, _]): Seq[A] = {
    Await.result(source.runWith(Sink.seq), 10.seconds)
  }

  override protected def afterAll(): Unit = {
    server.stop()
    super.afterAll()
  }
}
