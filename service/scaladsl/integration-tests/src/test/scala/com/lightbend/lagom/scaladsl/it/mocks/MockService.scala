/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.it.mocks

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import akka.stream.Materializer
import akka.Done
import akka.NotUsed
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.CircuitBreaker
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.NegotiatedDeserializer
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.NegotiatedSerializer
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer
import com.lightbend.lagom.scaladsl.api.deser.StrictMessageSerializer
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import play.api.libs.json.Format
import play.api.libs.json.Json

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
            case other                                             => throw DeserializationException("Bad request")
          }
        }

      override def serializerForResponse(acceptedMessageProtocols: immutable.Seq[MessageProtocol]) = Serializer
    }
}

case class MockResponseEntity(incomingId: Long, incomingRequest: MockRequestEntity)
object MockResponseEntity {
  implicit val format: Format[MockResponseEntity] = Json.format
}

case class MockAnyVal(value: Long) extends AnyVal
object MockAnyVal {
  implicit val format: Format[MockAnyVal] = Json.valueFormat
}

case class MockAnyValResponseEntity(incomingId: MockAnyVal, incomingRequest: MockRequestEntity)
object MockAnyValResponseEntity {
  implicit val format: Format[MockAnyValResponseEntity] = Json.format
}

trait MockService extends Service {

  def mockCall(id: Long): ServiceCall[MockRequestEntity, MockResponseEntity]
  def mockAnyValCall(id: MockAnyVal): ServiceCall[MockRequestEntity, MockAnyValResponseEntity]
  def doNothing: ServiceCall[NotUsed, NotUsed]
  def alwaysFail: ServiceCall[NotUsed, NotUsed]
  def doneCall(): ServiceCall[Done, Done]
  def streamResponse: ServiceCall[MockRequestEntity, Source[MockResponseEntity, NotUsed]]
  def unitStreamResponse: ServiceCall[NotUsed, Source[MockResponseEntity, NotUsed]]
  def streamRequest: ServiceCall[Source[MockRequestEntity, NotUsed], MockResponseEntity]
  def streamRequestUnit: ServiceCall[Source[MockRequestEntity, NotUsed], NotUsed]
  def bidiStream: ServiceCall[Source[MockRequestEntity, NotUsed], Source[MockResponseEntity, NotUsed]]
  def customHeaders: ServiceCall[String, String]
  def streamCustomHeaders: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]
  def serviceName: ServiceCall[NotUsed, String]
  def streamServiceName: ServiceCall[NotUsed, Source[String, NotUsed]]
  def queryParamId(query: Option[String]): ServiceCall[NotUsed, String]
  def listResults: ServiceCall[MockRequestEntity, List[MockResponseEntity]]
  def customContentType: ServiceCall[MockRequestEntity, MockResponseEntity]
  def noContentType: ServiceCall[MockRequestEntity, MockResponseEntity]
  def echoByteString: ServiceCall[ByteString, ByteString]

  override def descriptor = {
    named("mockservice").withCalls(
      restCall(Method.POST, "/mock/:id", mockCall _),
      restCall(Method.POST, "/mock-any-val/:id", mockAnyValCall _),
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
      call(customContentType _)(
        MockRequestEntity.customSerializer(Some("application/mock-request-entity")),
        implicitly[MessageSerializer[MockResponseEntity, _]]
      ),
      call(noContentType _)(
        MockRequestEntity.customSerializer(None),
        implicitly[MessageSerializer[MockResponseEntity, _]]
      ),
      call(echoByteString _)
    )
  }
}

object MockService {
  val invoked       = new AtomicBoolean
  val firstReceived = new AtomicReference[MockRequestEntity]()
}

class MockServiceImpl(implicit mat: Materializer, ec: ExecutionContext) extends MockService {

  override def mockCall(id: Long) = ServiceCall { req =>
    Future.successful(MockResponseEntity(id, req))
  }

  override def mockAnyValCall(id: MockAnyVal) = ServiceCall { req =>
    Future.successful(MockAnyValResponseEntity(id, req))
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
    Future.successful(Source(1 to 3).map { i =>
      MockResponseEntity(i, req)
    })
  }

  override def unitStreamResponse = ServiceCall { _ =>
    Future.successful(Source(1 to 3).map { i =>
      MockResponseEntity(i, MockRequestEntity("entity", i))
    })
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

  override def echoByteString = ServiceCall { req =>
    Future.successful(req)
  }

  private def withServiceName[Request, Response](
      block: String => ServerServiceCall[Request, Response]
  ): ServerServiceCall[Request, Response] = {
    ServerServiceCall.compose { requestHeader =>
      val serviceName = requestHeader.principal.map(_.getName).getOrElse {
        throw NotFound("principal")
      }
      block(serviceName)
    }
  }
}
