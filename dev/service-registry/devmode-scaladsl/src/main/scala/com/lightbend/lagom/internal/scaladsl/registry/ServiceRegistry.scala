/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.registry

import java.net.URI

import akka.NotUsed
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import com.lightbend.lagom.scaladsl.api.deser.{ MessageSerializer, StrictMessageSerializer }
import com.lightbend.lagom.scaladsl.api.transport.{ MessageProtocol, Method }
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceAcl, ServiceCall }
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.immutable
import scala.collection.immutable.Seq

/**
 * This mirrors com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry. The service locator implements the
 * javadsl version, but this is used to talk to it from Scala apps, and so they must be kept in sync.
 */
trait ServiceRegistry extends Service {

  def register(name: String): ServiceCall[ServiceRegistryService, NotUsed]
  def unregister(name: String): ServiceCall[NotUsed, NotUsed]
  def lookup(name: String): ServiceCall[NotUsed, URI]
  def registeredServices: ServiceCall[NotUsed, immutable.Seq[RegisteredService]]

  import Service._
  import ServiceRegistry._

  def descriptor: Descriptor = {
    named(ServiceName).withCalls(
      restCall(Method.PUT, "/services/:id", register _),
      restCall(Method.DELETE, "/services/:id", this.unregister _),
      restCall(Method.GET, "/services/:id", lookup _),
      pathCall("/services", registeredServices)
    ).withLocatableService(false)
  }
}

object ServiceRegistry {
  val ServiceName = "lagom-service-registry"

  implicit val uriMessageSerializer: MessageSerializer[URI, ByteString] = new StrictMessageSerializer[URI] {

    private val serializer = new NegotiatedSerializer[URI, ByteString] {
      override def serialize(message: URI): ByteString = ByteString.fromString(message.toString, "utf-8")
      override val protocol: MessageProtocol = MessageProtocol.empty.withContentType("text/plain").withCharset("utf-8")
    }
    override def serializerForRequest = serializer
    override def serializerForResponse(acceptedMessageProtocols: Seq[MessageProtocol]) = serializer

    override def deserializer(protocol: MessageProtocol): NegotiatedDeserializer[URI, ByteString] =
      new NegotiatedDeserializer[URI, ByteString] {
        override def deserialize(wire: ByteString) =
          URI.create(wire.decodeString(protocol.charset.getOrElse("utf-8")))
      }
  }

}

case class RegisteredService(name: String, url: URI)

object RegisteredService {
  import UriFormat.uriFormat
  implicit val format: Format[RegisteredService] = Json.format[RegisteredService]
}

case class ServiceRegistryService(uri: URI, acls: immutable.Seq[ServiceAcl])

object ServiceRegistryService {
  import UriFormat.uriFormat

  implicit val methodFormat: Format[Method] =
    (__ \ "name").format[String].inmap(new Method(_), _.name)

  implicit val serviceAclFormat: Format[ServiceAcl] = (
    (__ \ "method").formatNullable[Method] and
    (__ \ "pathRegex").formatNullable[String]
  ).apply(ServiceAcl.apply, acl => (acl.method, acl.pathRegex))

  implicit val format: Format[ServiceRegistryService] = Json.format[ServiceRegistryService]
}

object UriFormat {
  implicit val uriFormat: Format[URI] =
    implicitly[Format[String]].inmap(URI.create, _.toString)
}