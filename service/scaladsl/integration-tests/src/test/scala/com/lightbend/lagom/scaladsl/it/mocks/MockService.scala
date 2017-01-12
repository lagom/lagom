/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.it.mocks

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import akka.stream.Materializer
import akka.{ Done, NotUsed }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.{ CircuitBreaker, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import com.lightbend.lagom.scaladsl.api.deser.{ MessageSerializer, StrictMessageSerializer }
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import play.api.libs.json.Json

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

case class MockRequestEntity(field1: String, field2: Int)

object MockRequestEntity {
  implicit val format = Json.format[MockRequestEntity]
  def customSerializer(contentType: Option[String]): MessageSerializer[MockRequestEntity, ByteString] =
    new StrictMessageSerializer[MockRequestEntity] {
      override def acceptResponseProtocols =
        contentType.map(MessageProtocol.empty.withContentType).to[immutable.Seq]

      object Serializer extends NegotiatedSerializer[MockRequestEntity, ByteString] {
        override def protocol =
          contentType.fold(MessageProtocol.empty)(MessageProtocol.empty.withContentType)
        override def serialize(message: MockRequestEntity) =
          ByteString.fromString(s"${message.field1}:${message.field2}")
      }

      override def serializerForRequest = Serializer

      override def deserializer(protocol: MessageProtocol) =
        new NegotiatedDeserializer[MockRequestEntity, ByteString] {
          override def deserialize(wire: ByteString) = wire.utf8String.split(":") match {
            case Array(field1, field2) if field2.matches("-?\\d+") => MockRequestEntity(field1, field2.toInt)
            case other => throw DeserializationException("Bad request")
          }
        }

      override def serializerForResponse(acceptedMessageProtocols: immutable.Seq[MessageProtocol]) = Serializer
    }
}

case class MockResponseEntity(incomingId: Long, incomingRequest: MockRequestEntity)
object MockResponseEntity {
  implicit val format = Json.format[MockResponseEntity]
}

trait MockService extends Service {

  def mockCall(id: Long): ServiceCall[MockRequestEntity, MockResponseEntity]
  def doNothing: ServiceCall[NotUsed, NotUsed]
  def alwaysFail: ServiceCall[NotUsed, NotUsed]
  def doneCall(): ServiceCall[Done, Done]
  def streamResponse: ServiceCall[MockRequestEntity, Source[MockResponseEntity, _]]
  def unitStreamResponse: ServiceCall[NotUsed, Source[MockResponseEntity, _]]
  def streamRequest: ServiceCall[Source[MockRequestEntity, _], MockResponseEntity]
  def streamRequestUnit: ServiceCall[Source[MockRequestEntity, _], NotUsed]
  def bidiStream: ServiceCall[Source[MockRequestEntity, _], Source[MockResponseEntity, _]]
  def customHeaders: ServiceCall[String, String]
  def streamCustomHeaders: ServiceCall[Source[String, _], Source[String, _]]
  def serviceName: ServiceCall[NotUsed, String]
  def streamServiceName: ServiceCall[NotUsed, Source[String, _]]
  def queryParamId(query: Option[String]): ServiceCall[NotUsed, String]
  def listResults: ServiceCall[MockRequestEntity, List[MockResponseEntity]]
  def customContentType: ServiceCall[MockRequestEntity, MockResponseEntity]
  def noContentType: ServiceCall[MockRequestEntity, MockResponseEntity]

  override def descriptor = {
    named("mockservice").withCalls(
      restCall(Method.POST, "/mock/:id", mockCall _),
      call(doNothing _),
      call(alwaysFail _).withCircuitBreaker(CircuitBreaker.identifiedBy("foo")),
      call(doneCall _),
      call(streamResponse _),
      call(unitStreamResponse _),
      call(streamRequest _),
      call(streamRequestUnit _),
      call(bidiStream _),
      call(customHeaders _),
      call(streamCustomHeaders _),
      call(serviceName _),
      call(streamServiceName _),
      pathCall("/queryparam?qp", queryParamId _),
      call(listResults _),
      call(customContentType _)(MockRequestEntity.customSerializer(Some("application/mock-request-entity")), implicitly[MessageSerializer[MockResponseEntity, _]]),
      call(noContentType _)(MockRequestEntity.customSerializer(None), implicitly[MessageSerializer[MockResponseEntity, _]])
    )
  }
}

object MockService {
  val invoked = new AtomicBoolean
  val firstReceived = new AtomicReference[MockRequestEntity]()
}

class MockServiceImpl(implicit mat: Materializer, ec: ExecutionContext) extends MockService {

  override def mockCall(id: Long) = ServiceCall { req =>
    Future.successful(MockResponseEntity(id, req))
  }

  override def doNothing = ServiceCall { _ =>
    MockService.invoked.set(true)
    Future.successful(NotUsed)
  }

  override def alwaysFail = ServiceCall { _ =>
    MockService.invoked.set(true)
    throw new RuntimeException("Simulated error")
  }

  override def doneCall() = ServiceCall { done =>
    Future.successful(Done)
  }

  override def streamResponse = ServiceCall { req =>
    Future.successful(Source(1 to 3).map { i => MockResponseEntity(i, req) })
  }

  override def unitStreamResponse = ServiceCall { _ =>
    Future.successful(Source(1 to 3).map { i => MockResponseEntity(i, MockRequestEntity("entity", i)) })
  }

  override def streamRequest = ServiceCall { stream =>
    stream.runWith(Sink.head).map(head => MockResponseEntity(1, head))
  }

  override def streamRequestUnit = ServiceCall { stream =>
    stream.runWith(Sink.head).map { head =>
      MockService.firstReceived.set(head)
      NotUsed
    }
  }

  override def bidiStream = ServiceCall { stream =>
    Future.successful(stream.map(req => MockResponseEntity(1, req)))
  }

  override def customHeaders = ServerServiceCall { (requestHeader, headerName) =>
    val headerValue = requestHeader.getHeader(headerName).getOrElse {
      throw NotFound("Header " + headerName)
    }
    Future.successful((ResponseHeader.Ok.withStatus(201).withHeader("Header-Name", headerName), headerValue))
  }

  override def streamCustomHeaders = ServerServiceCall { (requestHeader, headerNames) =>
    Future.successful((ResponseHeader.Ok, headerNames.map { headerName =>
      requestHeader.getHeader(headerName).getOrElse {
        throw NotFound("Header " + headerName)
      }
    }))
  }

  override def serviceName = withServiceName { name =>
    ServerServiceCall { _ =>
      Future.successful(name)
    }
  }

  override def streamServiceName = withServiceName { name =>
    ServerServiceCall { _ =>
      Future.successful(Source.single(name))
    }
  }

  override def queryParamId(query: Option[String]) = ServiceCall { _ =>
    Future.successful(query.getOrElse("none"))
  }

  override def listResults = ServiceCall { req =>
    Future.successful((for (i <- 1 to req.field2) yield MockResponseEntity(i, req)).to[List])
  }

  override def customContentType = ServiceCall { req =>
    Future.successful(MockResponseEntity(req.field2, req))
  }

  override def noContentType = ServiceCall { req =>
    Future.successful(MockResponseEntity(req.field2, req))
  }

  private def withServiceName[Request, Response](block: String => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] = {
    ServerServiceCall.compose { requestHeader =>
      val serviceName = requestHeader.principal.map(_.getName).getOrElse {
        throw NotFound("principal")
      }
      block(serviceName)
    }
  }
}
