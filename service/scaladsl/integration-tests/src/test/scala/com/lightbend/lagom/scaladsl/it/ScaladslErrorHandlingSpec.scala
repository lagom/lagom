/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.it

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.lightbend.lagom.internal.scaladsl.client.ScaladslServiceClient
import com.lightbend.lagom.scaladsl.api.{ Descriptor, ServiceCall }
import com.lightbend.lagom.scaladsl.api.Descriptor.{ Call, CallId, NamedCallId, RestCallId }
import com.lightbend.lagom.scaladsl.api.ServiceSupport.ScalaMethodServiceCall
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import com.lightbend.lagom.scaladsl.api.deser.{ MessageSerializer, StreamedMessageSerializer, StrictMessageSerializer }
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.client.ServiceResolver
import com.lightbend.lagom.scaladsl.it.mocks.{ MockRequestEntity, MockService, MockServiceImpl }
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest.{ Matchers, WordSpec }
import play.api.libs.streams.AkkaStreams
import play.api.{ Environment, Mode }
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.immutable
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * A brief explanation of this spec.
 *
 * It checks that error handling works in all combinations of strict/streamed request/responses.
 *
 * In order to inject errors, we create and resolve the service descriptor, and then replace specific parts, for
 * example, the request or response serializer on either the server or the client, or the service call, with
 * components that throw the errors that we want to test the handling for.  We then have a suite of tests (in the
 * test method) that defines all these errors and tests.  The actual making of the call though is abstracted away,
 * this suite of tests is then executed once for each combination of strict/streamed request/responses, which tells
 * the test suite which endpoint in the descriptor to modify, and how to make a call to that endpoint.
 */
class ScaladslErrorHandlingSpec extends WordSpec with Matchers {

  "Service error handling" when {
    "handling errors with plain HTTP calls" should {
      tests(RestCallId(Method.POST, "/mock/:id")) { implicit mat => client =>
        val result = client.mockCall(1).invoke(new MockRequestEntity("b", 2))
        try {
          Await.result(result, 10.seconds)
          throw sys.error("Did not fail")
        } catch {
          case NonFatal(other) => other
        }
      }
    }

    "handling errors with streamed response calls" should {
      tests(NamedCallId("streamResponse")) { implicit mat => client =>
        val result = client.streamResponse.invoke(new MockRequestEntity("b", 2))
        try {
          val resultSource = Await.result(result, 10.seconds)
          Await.result(resultSource.runWith(Sink.ignore), 10.seconds)
          throw sys.error("No error was thrown")
        } catch {
          case NonFatal(other) => other
        }
      }
    }

    "handling errors with streamed request calls" should {
      tests(NamedCallId("streamRequest")) { implicit mat => client =>
        val result = client.streamRequest
          .invoke(Source.single(new MockRequestEntity("b", 2)).concat(Source.maybe))
        try {
          Await.result(result, 10.seconds)
          throw sys.error("No error was thrown")
        } catch {
          case NonFatal(other) => other
        }
      }
    }

    "handling errors with bidirectional streamed calls" should {
      tests(NamedCallId("bidiStream")) { implicit mat => client =>
        val result = client.bidiStream
          .invoke(Source.single(new MockRequestEntity("b", 2)).concat(Source.maybe))
        try {
          val resultSource = Await.result(result, 10.seconds)
          Await.result(resultSource.runWith(Sink.ignore), 10.seconds)
          throw sys.error("No error was thrown")
        } catch {
          case NonFatal(other) => other
        }
      }
    }
  }

  def tests(callId: CallId)(makeCall: Materializer => MockService => Throwable) = {
    "handle errors in request serialization" in withClient(changeClient = change(callId)(failingRequestSerializer)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: SerializationException =>
          e.errorCode should ===(TransportErrorCode.InternalServerError)
          e.exceptionMessage.detail should ===("failed serialize")
      }
    }
    "handle errors in request deserialization negotiation" in withClient(changeServer = change(callId)(failingRequestNegotiation)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: UnsupportedMediaType =>
          e.errorCode should ===(TransportErrorCode.UnsupportedMediaType)
          e.exceptionMessage.detail should include("application/json")
          e.exceptionMessage.detail should include("unsupported")
      }
    }
    "handle errors in request deserialization" in withClient(changeServer = change(callId)(failingRequestSerializer)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: DeserializationException =>
          e.errorCode should ===(TransportErrorCode.UnsupportedData)
          e.exceptionMessage.detail should ===("failed deserialize")
      }
    }
    "handle errors in service call invocation" in withClient(changeServer = change(callId)(failingServiceCall)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: TransportException =>
          // By default, we don't give out internal details of exceptions, for security reasons
          e.exceptionMessage.name should ===("Exception")
          e.exceptionMessage.detail should ===("")
          e.errorCode should ===(TransportErrorCode.InternalServerError)
      }
    }
    "handle asynchronous errors in service call invocation" in withClient(changeServer = change(callId)(asyncFailingServiceCall)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: TransportException =>
          e.exceptionMessage.name should ===("Exception")
          e.exceptionMessage.detail should ===("")
          e.errorCode should ===(TransportErrorCode.InternalServerError)
      }
    }
    "handle stream errors in service call invocation" when {
      "in prod mode will not give out error information" in withClient(changeServer = change(callId)(failingStreamedServiceCall)) { implicit mat => client =>
        makeCall(mat)(client) match {
          case e: TransportException =>
            e.exceptionMessage.name should ===("Exception")
            e.exceptionMessage.detail should ===("")
            e.errorCode should ===(TransportErrorCode.InternalServerError)
        }
      }
      "in dev mode will give out detailed exception information" in withClient(changeServer = change(callId)(failingStreamedServiceCall), mode = Mode.Dev) { implicit mat => client =>
        makeCall(mat)(client) match {
          case e: TransportException =>
            e.errorCode should ===(TransportErrorCode.InternalServerError)
            e.exceptionMessage.name match {
              case "java.lang.RuntimeException: service call failed" =>
                // It should contain a stack trace in the information
                e.exceptionMessage.detail should include("at com.lightbend.lagom.scaladsl.it.")
              case "Error message truncated" =>
                e.exceptionMessage.detail should ===("")
              case other =>
                fail("Unknown exception massage name: " + other)
            }
        }
      }
    }
    "handle errors in response serialization negotiation" in withClient(changeServer = change(callId)(failingResponseNegotation)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: NotAcceptable =>
          e.errorCode should ===(TransportErrorCode.NotAcceptable)
          e.exceptionMessage.detail should include("application/json")
          e.exceptionMessage.detail should include("not accepted")
      }
    }
    "handle errors in response serialization" in withClient(changeServer = change(callId)(failingResponseSerializer)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: SerializationException =>
          e.errorCode should ===(TransportErrorCode.InternalServerError)
          e.exceptionMessage.detail should ===("failed serialize")
      }
    }
    "handle errors in response deserialization negotiation" in withClient(changeClient = change(callId)(failingResponseNegotation)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: UnsupportedMediaType =>
          e.errorCode should ===(TransportErrorCode.UnsupportedMediaType)
          e.exceptionMessage.detail should include("unsupported")
          try {
            e.exceptionMessage.detail should include("application/json")
          } catch {
            case NonFatal(e) => println("SKIPPED - Requires https://github.com/playframework/playframework/issues/5322")
          }
      }
    }
    "handle errors in response deserialization" in withClient(changeClient = change(callId)(failingResponseSerializer)) { implicit mat => client =>
      makeCall(mat)(client) match {
        case e: DeserializationException =>
          e.errorCode should ===(TransportErrorCode.UnsupportedData)
          e.exceptionMessage.detail should ===("failed deserialize")
      }
    }
  }

  /**
   * This sets up the server and the client, but allows them to be modified before actually creating them.
   */
  def withClient(changeClient: Descriptor => Descriptor = identity, changeServer: Descriptor => Descriptor = identity,
                 mode: Mode.Mode = Mode.Prod)(block: Materializer => MockService => Unit): Unit = {

    ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
      new LagomApplication(ctx) with AhcWSComponents with LocalServiceLocator {
        override lazy val lagomServer = LagomServer.forServices(
          bindService[MockService].to(new MockServiceImpl)
        )
        override lazy val environment = Environment.simple(mode = mode)

        // Custom server builder to inject our changeServer callback
        override lazy val lagomServerBuilder = new LagomServerBuilder(httpConfiguration, new ServiceResolver {
          override def resolve(descriptor: Descriptor): Descriptor = changeServer(serviceResolver.resolve(descriptor))
        })(materializer, executionContext)

        // Custom service client to inject our changeClient callback
        override lazy val serviceClient = new ScaladslServiceClient(wsClient, scaladslWebSocketClient, serviceInfo,
          serviceLocator, new ServiceResolver {
          override def resolve(descriptor: Descriptor): Descriptor = changeClient(serviceResolver.resolve(descriptor))
        }, None)(executionContext, materializer)
      }
    } { server =>
      val client = server.serviceClient.implement[MockService]
      block(server.materializer)(client)
    }
  }

  def change(callId: CallId)(changer: Call[_, _] => Call[_, _]): Descriptor => Descriptor = { descriptor =>
    val newCalls = descriptor.calls.map {
      case call if call.callId == callId => changer(call)
      case other                         => other
    }
    descriptor.withCalls(newCalls: _*)
  }

  def failingRequestSerializer: Call[_, _] => Call[_, _] = { call =>
    if (call.requestSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[Source[Any, NotUsed], Any]]
        .withRequestSerializer(MessageSerializer.sourceMessageSerializer(failingSerializer))
    } else {
      call.asInstanceOf[Call[Any, Any]].withRequestSerializer(failingSerializer)
    }
  }

  def failingResponseSerializer: Call[_, _] => Call[_, _] = { call =>
    if (call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[Any, Source[Any, NotUsed]]]
        .withResponseSerializer(MessageSerializer.sourceMessageSerializer(failingSerializer))
    } else {
      call.asInstanceOf[Call[Any, Any]].withResponseSerializer(failingSerializer)
    }
  }

  def failingSerializer = new StrictMessageSerializer[Any] {
    val failedSerializer = new NegotiatedSerializer[Any, ByteString] {
      override def serialize(messageEntity: Any): ByteString = throw SerializationException("failed serialize")
    }
    override def deserializer(messageHeader: MessageProtocol) = new NegotiatedDeserializer[Any, ByteString] {
      override def deserialize(wire: ByteString): AnyRef = throw DeserializationException("failed deserialize")
    }
    override def serializerForResponse(acceptedMessageHeaders: immutable.Seq[MessageProtocol]) = failedSerializer
    override def serializerForRequest = failedSerializer
  }

  def failingRequestNegotiation: Call[_, _] => Call[_, _] = { call =>
    if (call.requestSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[Source[Any, NotUsed], Any]]
        .withRequestSerializer(MessageSerializer.sourceMessageSerializer(failingNegotiation))
    } else {
      call.asInstanceOf[Call[Any, Any]].withRequestSerializer(failingNegotiation)
    }
  }

  def failingResponseNegotation: Call[_, _] => Call[_, _] = { call =>
    if (call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[Any, Source[Any, NotUsed]]]
        .withResponseSerializer(MessageSerializer.sourceMessageSerializer(failingNegotiation))
    } else {
      call.asInstanceOf[Call[Any, Any]].withResponseSerializer(failingNegotiation)
    }
  }

  def failingNegotiation = new StrictMessageSerializer[Any] {
    override def serializerForRequest: NegotiatedSerializer[Any, ByteString] =
      throw new NotImplementedError("Can't fail negotiation for request")

    override def deserializer(messageHeader: MessageProtocol): NegotiatedDeserializer[Any, ByteString] =
      throw UnsupportedMediaType(messageHeader, MessageProtocol.empty.withContentType("unsupported"))

    override def serializerForResponse(acceptedMessageHeaders: immutable.Seq[MessageProtocol]): NegotiatedSerializer[Any, ByteString] = {
      throw NotAcceptable(acceptedMessageHeaders, MessageProtocol.empty.withContentType("not accepted"))
    }
  }

  def overrideServiceCall(serviceCall: ServiceCall[_, _]): Call[_, _] => Call[_, _] = { call =>
    call.serviceCallHolder match {
      case scalaMethodCall: ScalaMethodServiceCall[_, _] =>
        call.asInstanceOf[Call[Any, Any]].withServiceCallHolder(new ScalaMethodServiceCall[Any, Any](scalaMethodCall.method, scalaMethodCall.pathParamSerializers) {
          override def invoke(service: Any, parameters: immutable.Seq[AnyRef]) = serviceCall
        })
    }
  }

  def failingServiceCall: Call[_, _] => Call[_, _] = overrideServiceCall(ServiceCall[Any, Any] { _ =>
    throw new RuntimeException("service call failed")
  })

  def asyncFailingServiceCall: Call[_, _] => Call[_, _] = overrideServiceCall(ServiceCall[Any, Any] { _ =>
    Future.failed[Any](new RuntimeException("service call failed"))
  })

  def failingStreamedServiceCall: Call[_, _] => Call[_, _] = { call =>
    // If the response is not streamed, then just return a failing service call
    if (call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      overrideServiceCall(ServiceCall[Any, Any] { request =>
        Future.successful(request match {
          case stream: Source[Any, _] =>
            stream via AkkaStreams.ignoreAfterCancellation map { _ =>
              throw new RuntimeException("service call failed")
            }
          case _ =>
            Source.failed(throw new RuntimeException("service call failed"))
        })
      })(call)
    } else {
      failingServiceCall(call)
    }
  }

}
