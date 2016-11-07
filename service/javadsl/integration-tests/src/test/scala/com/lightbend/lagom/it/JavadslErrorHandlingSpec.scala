/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it

import java.util
import java.util.concurrent.{ CompletableFuture, CompletionStage, ExecutionException, TimeUnit }
import javax.inject.Singleton
import javax.inject.{ Inject, Provider }

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.javadsl.{ Source => JSource }
import akka.util.ByteString
import com.lightbend.lagom.internal.api._
import com.lightbend.lagom.internal.server._
import com.lightbend.lagom.it.mocks._
import com.lightbend.lagom.javadsl.api.Descriptor.{ Call, CallId, NamedCallId, RestCallId }
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import com.lightbend.lagom.javadsl.api.deser.{ DeserializationException, SerializationException, StreamedMessageSerializer, StrictMessageSerializer }
import com.lightbend.lagom.javadsl.api.transport._
import com.lightbend.lagom.javadsl.api._
import com.lightbend.lagom.javadsl.jackson.{ JacksonExceptionSerializer, JacksonSerializerFactory }
import org.pcollections.TreePVector
import play.api.libs.streams.AkkaStreams
import play.api.{ Application, Environment, Mode }
import play.api.inject._

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import com.typesafe.config.ConfigFactory
import akka.actor.ReflectiveDynamicAccess
import com.lightbend.lagom.internal.jackson.JacksonObjectMapperProvider
import com.lightbend.lagom.internal.javadsl.api._
import com.lightbend.lagom.internal.javadsl.client.JavadslServiceClientImplementor
import com.lightbend.lagom.internal.javadsl.server.{ ResolvedService, ResolvedServices, JavadslServerBuilder, ServiceInfoProvider }

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
class JavadslErrorHandlingSpec extends ServiceSupport {

  "Service error handling" when {
    "handling errors with plain HTTP calls" should {
      tests(new RestCallId(Method.POST, "/mock/:id")) { implicit app => client =>
        val result = client.mockCall(1).invoke(new MockRequestEntity("b", 2))
        try {
          result.toCompletableFuture.get(10, TimeUnit.SECONDS)
          throw sys.error("Did not fail")
        } catch {
          case e: ExecutionException => e.getCause
        }
      }
    }

    "handling errors with streamed response calls" should {
      tests(new NamedCallId("streamResponse")) { implicit app => client =>
        val result = client.streamResponse().invoke(new MockRequestEntity("b", 2))
        try {
          val resultSource = result.toCompletableFuture.get(10, TimeUnit.SECONDS)
          Await.result(resultSource.asScala.runWith(Sink.ignore), 10.seconds)
          throw sys.error("No error was thrown")
        } catch {
          case e: ExecutionException => e.getCause
          case NonFatal(other)       => other
        }
      }
    }

    "handling errors with streamed request calls" should {
      tests(new NamedCallId("streamRequest")) { implicit app => client =>
        val result = client.streamRequest()
          .invoke(Source.single(new MockRequestEntity("b", 2)).concat(Source.maybe).asJava)
        try {
          result.toCompletableFuture.get(10, TimeUnit.SECONDS)
          throw sys.error("No error was thrown")
        } catch {
          case e: ExecutionException => e.getCause
          case NonFatal(other)       => other
        }
      }
    }

    "handling errors with bidirectional streamed calls" should {
      tests(new NamedCallId("bidiStream")) { implicit app => client =>
        val result = client.bidiStream()
          .invoke(Source.single(new MockRequestEntity("b", 2)).concat(Source.maybe).asJava)
        try {
          val resultSource = result.toCompletableFuture.get(10, TimeUnit.SECONDS)
          Await.result(resultSource.asScala.runWith(Sink.ignore), 10.seconds)
          throw sys.error("No error was thrown")
        } catch {
          case e: ExecutionException => e.getCause
          case NonFatal(other)       => other
        }
      }
    }
  }

  def tests(callId: CallId)(makeCall: Application => MockService => Throwable) = {
    "handle errors in request serialization" in withClient(changeClient = change(callId)(failingRequestSerializer)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: SerializationException =>
          e.errorCode should ===(TransportErrorCode.InternalServerError)
          e.exceptionMessage.detail should ===("failed serialize")
      }
    }
    "handle errors in request deserialization negotiation" in withClient(changeServer = change(callId)(failingRequestNegotiation)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: UnsupportedMediaType =>
          e.errorCode should ===(TransportErrorCode.UnsupportedMediaType)
          e.exceptionMessage.detail should include("application/json")
          e.exceptionMessage.detail should include("unsupported")
      }
    }
    "handle errors in request deserialization" in withClient(changeServer = change(callId)(failingRequestSerializer)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: DeserializationException =>
          e.errorCode should ===(TransportErrorCode.UnsupportedData)
          e.exceptionMessage.detail should ===("failed deserialize")
      }
    }
    "handle errors in service call invocation" in withClient(changeServer = change(callId)(failingServiceCall)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: TransportException =>
          // By default, we don't give out internal details of exceptions, for security reasons
          e.exceptionMessage.name should ===("Exception")
          e.exceptionMessage.detail should ===("")
          e.errorCode should ===(TransportErrorCode.InternalServerError)
      }
    }
    "handle asynchronous errors in service call invocation" in withClient(changeServer = change(callId)(asyncFailingServiceCall)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: TransportException =>
          e.exceptionMessage.name should ===("Exception")
          e.exceptionMessage.detail should ===("")
          e.errorCode should ===(TransportErrorCode.InternalServerError)
      }
    }
    "handle stream errors in service call invocation" when {
      "in prod mode will not give out error information" in withClient(changeServer = change(callId)(failingStreamedServiceCall)) { implicit app => client =>
        makeCall(app)(client) match {
          case e: TransportException =>
            e.exceptionMessage.name should ===("Exception")
            e.exceptionMessage.detail should ===("")
            e.errorCode should ===(TransportErrorCode.InternalServerError)
        }
      }
      "in dev mode will give out detailed exception information" in withClient(changeServer = change(callId)(failingStreamedServiceCall), mode = Mode.Dev) { implicit app => client =>
        makeCall(app)(client) match {
          case e: TransportException =>
            e.errorCode should ===(TransportErrorCode.InternalServerError)
            e.exceptionMessage.name match {
              case "java.lang.RuntimeException: service call failed" =>
                // It should contain a stack trace in the information
                e.exceptionMessage.detail should include("at com.lightbend.lagom.it.")
              case "Error message truncated" =>
                e.exceptionMessage.detail should ===("")
              case other =>
                fail("Unknown exception massage name: " + other)
            }
        }
      }
    }
    "handle errors in response serialization negotiation" in withClient(changeServer = change(callId)(failingResponseNegotation)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: NotAcceptable =>
          e.errorCode should ===(TransportErrorCode.NotAcceptable)
          e.exceptionMessage.detail should include("application/json")
          e.exceptionMessage.detail should include("not accepted")
      }
    }
    "handle errors in response serialization" in withClient(changeServer = change(callId)(failingResponseSerializer)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: SerializationException =>
          e.errorCode should ===(TransportErrorCode.InternalServerError)
          e.exceptionMessage.detail should ===("failed serialize")
      }
    }
    "handle errors in response deserialization negotiation" in withClient(changeClient = change(callId)(failingResponseNegotation)) { implicit app => client =>
      makeCall(app)(client) match {
        case e: UnsupportedMediaType =>
          e.errorCode should ===(TransportErrorCode.UnsupportedMediaType)
          e.exceptionMessage.detail should include("unsupported")
          try {
            e.exceptionMessage.detail should include("application/json")
          } catch {
            case e => println("SKIPPED - Requires https://github.com/playframework/playframework/issues/5322")
          }
      }
    }
    "handle errors in response deserialization" in withClient(changeClient = change(callId)(failingResponseSerializer)) { implicit app => client =>
      makeCall(app)(client) match {
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
                 mode: Mode.Mode = Mode.Prod)(block: Application => MockService => Unit): Unit = {

    val environment = Environment.simple(mode = mode)
    val jacksonSerializerFactory = new JacksonSerializerFactory(new JacksonObjectMapperProvider(
      ConfigFactory.load(), new ReflectiveDynamicAccess(environment.classLoader), None
    ).objectMapper)
    val jacksonExceptionSerializer = new JacksonExceptionSerializer(new play.Environment(environment))
    val descriptor = ServiceReader.readServiceDescriptor(environment.classLoader, classOf[MockService])
    val resolved = ServiceReader.resolveServiceDescriptor(descriptor, environment.classLoader,
      Map(JacksonPlaceholderSerializerFactory -> jacksonSerializerFactory),
      Map(JacksonPlaceholderExceptionSerializer -> jacksonExceptionSerializer))

    withServer(
      _.bindings(
        bind[ServiceInfo].to(new ServiceInfoProvider(classOf[MockService]))
      )
        .overrides(bind[ResolvedServices].to(new MockResolvedServicesProvider(resolved, changeServer)))
    ) { app =>
        val clientImplementor = app.injector.instanceOf[JavadslServiceClientImplementor]
        val clientDescriptor = changeClient(resolved)
        val client = clientImplementor.implement(classOf[MockService], clientDescriptor)
        block(app)(client)
      }
  }

  @Singleton
  class MockResolvedServicesProvider(descriptor: Descriptor, changeServer: Descriptor => Descriptor) extends Provider[ResolvedServices] {
    @Inject var serverBuilder: JavadslServerBuilder = _
    @Inject var mockService: MockServiceImpl = _

    lazy val get = {
      val changed = changeServer(descriptor)
      new ResolvedServices(Seq(ResolvedService(classOf[MockService], mockService, changed)))
    }
  }

  def change(callId: CallId)(changer: Call[_, _] => Call[_, _]): Descriptor => Descriptor = { descriptor =>
    val newEndpoints = descriptor.calls.asScala.map { call =>
      if (call.callId == callId) {
        changer(call)
      } else call
    }
    descriptor.replaceAllCalls(TreePVector.from(newEndpoints.asJava))
  }

  def failingRequestSerializer: Call[_, _] => Call[_, _] = { call =>
    if (call.requestSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[JSource[Any, _], Any]]
        .withRequestSerializer(new DelegatingStreamedMessageSerializer(failingSerializer))
    } else {
      call.asInstanceOf[Call[Any, Any]].withRequestSerializer(failingSerializer)
    }
  }

  def failingResponseSerializer: Call[_, _] => Call[_, _] = { call =>
    if (call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[Any, JSource[Any, _]]]
        .withResponseSerializer(new DelegatingStreamedMessageSerializer(failingSerializer))
    } else {
      call.asInstanceOf[Call[Any, Any]].withResponseSerializer(failingSerializer)
    }
  }

  def failingSerializer = new StrictMessageSerializer[Any] {
    val failedSerializer = new NegotiatedSerializer[Any, ByteString] {
      override def serialize(messageEntity: Any): ByteString = throw new SerializationException("failed serialize")
    }
    override def deserializer(messageHeader: MessageProtocol) = new NegotiatedDeserializer[Any, ByteString] {
      override def deserialize(wire: ByteString): AnyRef = throw new DeserializationException("failed deserialize")
    }
    override def serializerForResponse(acceptedMessageHeaders: util.List[MessageProtocol]) =
      failedSerializer
    override def serializerForRequest() =
      failedSerializer
  }

  def failingRequestNegotiation: Call[_, _] => Call[_, _] = { call =>
    if (call.requestSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[JSource[Any, _], Any]]
        .withRequestSerializer(new DelegatingStreamedMessageSerializer(failingNegotiation))
    } else {
      call.asInstanceOf[Call[Any, Any]].withRequestSerializer(failingNegotiation)
    }
  }

  def failingResponseNegotation: Call[_, _] => Call[_, _] = { call =>
    if (call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      call.asInstanceOf[Call[Any, JSource[Any, _]]]
        .withResponseSerializer(new DelegatingStreamedMessageSerializer(failingNegotiation))
    } else {
      call.asInstanceOf[Call[Any, Any]].withResponseSerializer(failingNegotiation)
    }
  }

  def failingNegotiation = new StrictMessageSerializer[Any] {
    override def serializerForRequest(): NegotiatedSerializer[Any, ByteString] =
      throw new NotImplementedError("Can't fail negotiation for request")

    override def deserializer(messageHeader: MessageProtocol): NegotiatedDeserializer[Any, ByteString] =
      throw new UnsupportedMediaType(messageHeader, new MessageProtocol().withContentType("unsupported"))

    override def serializerForResponse(acceptedMessageHeaders: util.List[MessageProtocol]): NegotiatedSerializer[Any, ByteString] = {
      throw new NotAcceptable(acceptedMessageHeaders, new MessageProtocol().withContentType("not accepted"))
    }
  }

  def overrideServiceCall(serviceCall: ServiceCall[_, _]): Call[_, _] => Call[_, _] = { call =>
    call.asInstanceOf[Call[Any, Any]].withServiceCallHolder(new MethodServiceCallHolder {
      override def invoke(arguments: Seq[AnyRef]): Seq[Seq[String]] = ???
      override def create(service: Any, parameters: Seq[Seq[String]]): ServiceCall[_, _] = serviceCall
      override val method = null
    })
  }

  def failingServiceCall: Call[_, _] => Call[_, _] = overrideServiceCall(new ServiceCall[Any, Any] {
    override def invoke(request: Any): CompletionStage[Any] = throw new RuntimeException("service call failed")
  })

  def asyncFailingServiceCall: Call[_, _] => Call[_, _] = overrideServiceCall(new ServiceCall[Any, Any] {
    override def invoke(request: Any): CompletionStage[Any] =
      Future.failed[Any](new RuntimeException("service call failed")).toJava
  })

  def failingStreamedServiceCall: Call[_, _] => Call[_, _] = { call =>
    // If the response is not streamed, then just return a failing service call
    if (call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]) {
      overrideServiceCall(new ServiceCall[Any, JSource[Any, _]] {
        override def invoke(request: Any): CompletionStage[JSource[Any, _]] = {
          CompletableFuture.completedFuture(request match {
            case stream: JSource[Any, _] =>
              (stream.asScala via AkkaStreams.ignoreAfterCancellation map { _ =>
                throw new RuntimeException("service call failed")
              }).asJava
            case _ =>
              JSource.failed(throw new RuntimeException("service call failed"))
          })
        }
      })(call)
    } else {
      failingServiceCall(call)
    }
  }

}
