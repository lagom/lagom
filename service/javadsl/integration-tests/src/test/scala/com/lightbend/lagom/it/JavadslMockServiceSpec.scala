/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it

import java.util.Optional
import java.util.concurrent.TimeUnit
import akka.stream.scaladsl.{ Sink, Flow, Source }
import com.lightbend.lagom.it.mocks._
import akka.Done
import akka.NotUsed
import play.api.Application
import play.api.libs.streams.AkkaStreams
import scala.collection.JavaConverters._
import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration._
import scala.compat.java8.OptionConverters._
import scala.util.{ Failure, Success, Try }
import akka.pattern.CircuitBreakerOpenException
import java.util.concurrent.ExecutionException

class JavadslMockServiceSpec extends ServiceSupport {

  def withMockServiceClient(block: Application => MockService => Unit): Unit =
    withClient[MockService](_.bindings(new MockServiceModule))(block)

  "A mock service" should {
    "be possible to invoke" in withMockServiceClient { implicit app => client =>
      val id = 10L
      val request = new MockRequestEntity("bar", 20)
      val response = client.mockCall(id).invoke(request).toCompletableFuture.get(10, TimeUnit.SECONDS)
      response.incomingId should ===(id)
      response.incomingRequest should ===(request)
    }
    "be possible to invoke for NotUsed parameters" in withMockServiceClient { implicit app => client =>
      MockServiceImpl.invoked.set(false)
      client.doNothing().invoke().toCompletableFuture.get(10, TimeUnit.SECONDS) should ===(NotUsed)
      MockServiceImpl.invoked.get() should ===(true)
    }
    "be possible to invoke for Done parameters and resonse" in withMockServiceClient { implicit app => client =>
      val response = client.doneCall.invoke(Done.getInstance()).toCompletableFuture.get(10, TimeUnit.SECONDS)
      response should ===(Done.getInstance)
    }

    "work with streamed responses" in withMockServiceClient { implicit app => client =>
      val request = new MockRequestEntity("entity", 1)
      Try(client.streamResponse().invoke(request).toCompletableFuture.get(10, TimeUnit.SECONDS)) match {
        case Success(result) =>
          consume(result.asScala) should ===((1 to 3).map(i => new MockResponseEntity(i, request)))
        case Failure(_) =>
          println("SKIPPED - This may sometimes fail due to https://github.com/playframework/playframework/issues/5365")
      }
    }

    "work with streamed responses and unit requests" in withMockServiceClient { implicit app => client =>
      val resultStream = client.unitStreamResponse().invoke().toCompletableFuture.get(10, TimeUnit.SECONDS)
      consume(resultStream.asScala) should ===((1 to 3).map(i => new MockResponseEntity(i, new MockRequestEntity("entity", i))))
    }
    "work with streamed requests" in withMockServiceClient { implicit app => client =>
      val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
      val gotResponse = Promise[None.type]()
      val closeWhenGotResponse = Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
      val result = client.streamRequest().invoke(Source(requests).concat(closeWhenGotResponse).asJava).toCompletableFuture.get(10, TimeUnit.SECONDS)
      gotResponse.success(None)
      result should ===(new MockResponseEntity(1, requests(0)))
    }
    "work with streamed requests and unit responses" when {
      "an empty message is sent for unit" in withMockServiceClient { implicit app => client =>
        // In this case, we wait for a response from the server before closing the connection. The response will be an
        // empty web socket message which will be returned to us as null
        MockServiceImpl.firstReceived.set(null)
        val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
        val gotResponse = Promise[None.type]()
        val closeWhenGotResponse = Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
        client.streamRequestUnit().invoke(Source(requests).concat(closeWhenGotResponse).asJava).toCompletableFuture.get(10, TimeUnit.SECONDS) should ===(NotUsed)
        gotResponse.success(None)
        MockServiceImpl.firstReceived.get() should ===(requests(0))
      }
      "no message is sent for unit" in withMockServiceClient { implicit app => client =>
        // In this case, we send nothing to the server, which is waiting for a single message before it sends a response,
        // instead we just close the connection, we want to make sure that the client call still returns.
        MockServiceImpl.firstReceived.set(null)
        client.streamRequestUnit().invoke(Source.empty.asJava).toCompletableFuture.get(10, TimeUnit.SECONDS) should ===(NotUsed)
      }
    }
    "work with bidi streams" when {
      "the client closes the connection" in withMockServiceClient { implicit app => client =>
        val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
        // Use a source that never terminates so we don't close the upstream (which would close the downstream), and then
        // use takeUpTo so that we close downstream when we've got everything we want
        val resultStream = client.bidiStream().invoke(Source(requests).concat(Source.maybe).asJava).toCompletableFuture.get(10, TimeUnit.SECONDS)
        consume(resultStream.asScala via takeUpTo(3)) should ===(requests.map(r => new MockResponseEntity(1, r)))
      }
      "the server closes the connection" in withMockServiceClient { implicit app => client =>
        val requests = (1 to 3).map(i => new MockRequestEntity("request", i))
        val gotResponse = Promise[None.type]()
        val closeWhenGotResponse = Source.maybe[MockRequestEntity].mapMaterializedValue(_.completeWith(gotResponse.future))
        val serverClosed = Promise[Done]()
        val trackServerClosed = AkkaStreams.ignoreAfterCancellation[MockResponseEntity].mapMaterializedValue(serverClosed.completeWith)
        val resultStream = client.bidiStream().invoke(Source(requests).concat(closeWhenGotResponse).asJava).toCompletableFuture.get(10, TimeUnit.SECONDS)
        consume(resultStream.asScala via trackServerClosed via takeUpTo(3)) should ===(requests.map(r => new MockResponseEntity(1, r)))
        gotResponse.success(None)
        Await.result(serverClosed.future, 10.seconds) should ===(Done)
      }
    }
    "work with custom headers" in withMockServiceClient { implicit app => client =>
      val consumer = new MockServiceClientConsumer(client)
      val result = consumer.invokeCustomHeaders("Foo-Header", "Bar").toCompletableFuture.get(10, TimeUnit.SECONDS)
      result.second should ===("Bar")
      result.first.getHeader("Header-Name").asScala should ===(Some("Foo-Header"))
      result.first.status should ===(201)
    }
    "work with custom headers on streams" in withMockServiceClient { implicit app => client =>
      val consumer = new MockServiceClientConsumer(client)
      val result = consumer.invokeStreamCustomHeaders(
        Seq(akka.japi.Pair("Header-1", "value1"), akka.japi.Pair("Header-2", "value2")).asJava
      ).toCompletableFuture.get(10, TimeUnit.SECONDS)
      val values = consume(result.asScala.via(Flow[String].take(2)))
      values should ===(Seq("value1", "value2"))
    }
    "send the service name" in withMockServiceClient { implicit app => client =>
      client.serviceName().invoke().toCompletableFuture.get(10, TimeUnit.SECONDS) should ===("mockservice")
    }
    "send the service name on streams" in withMockServiceClient { implicit app => client =>
      Await.result(client.streamServiceName().invoke().toCompletableFuture.get(10, TimeUnit.SECONDS)
        .asScala.runWith(Sink.head), 10.seconds) should ===("mockservice")
    }

    "work with query params" in withMockServiceClient { implicit app => client =>
      client.queryParamId(Optional.of("foo")).invoke()
        .toCompletableFuture.get(10, TimeUnit.SECONDS) should ===("foo")
    }

    "work with collections of entities" in withMockServiceClient { implicit app => client =>
      val request = new MockRequestEntity("results", 10)
      val response = client.listResults().invoke(request).toCompletableFuture.get(10, TimeUnit.SECONDS)

      response.size() should ===(request.field2)
    }

    "work with custom serializers" when {
      "the serializer protocol uses a custom contentType" in withMockServiceClient { implicit app => client =>
        val id = 20
        val request = new MockRequestEntity("bar", id)
        val response = client.customContentType().invoke(request).toCompletableFuture.get(10, TimeUnit.SECONDS)
        response.incomingId should ===(id)
        response.incomingRequest should ===(request)
      }

      "the serializer protocol does not specify a contentType" in withMockServiceClient { implicit app => client =>
        val id = 20
        val request = new MockRequestEntity("bar", id)
        val response = client.noContentType().invoke(request).toCompletableFuture.get(10, TimeUnit.SECONDS)
        response.incomingId should ===(id)
        response.incomingRequest should ===(request)
      }
    }

    "be invoked with circuit breaker" in withMockServiceClient { implicit app => client =>
      MockServiceImpl.invoked.set(false)
      (1 to 20).foreach { _ =>
        intercept[Exception] {
          client.alwaysFail().invoke().toCompletableFuture.get(10, TimeUnit.SECONDS)
        }
      }
      MockServiceImpl.invoked.get() should ===(true)
      MockServiceImpl.invoked.set(false)
      intercept[CircuitBreakerOpenException] {
        try {
          client.alwaysFail().invoke().toCompletableFuture.get(10, TimeUnit.SECONDS)
        } catch {
          case e: ExecutionException => throw e.getCause
        }
      }
      MockServiceImpl.invoked.get() should ===(false)
    }

  }

}
