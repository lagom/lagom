/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.net.URI
import java.util.Collections
import java.util.Optional

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.ByteString
import com.lightbend.lagom.internal.javadsl.registry.{ RegisteredService => jRegisteredService }
import com.lightbend.lagom.internal.javadsl.registry.{ ServiceRegistryService => jServiceRegistryService }
import com.lightbend.lagom.internal.scaladsl.registry.{ RegisteredService => sRegisteredService }
import com.lightbend.lagom.internal.scaladsl.registry.{ ServiceRegistryService => sServiceRegistryService }
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer
import com.lightbend.lagom.javadsl.api.deser.StrictMessageSerializer
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol
import com.lightbend.lagom.javadsl.api.transport.Method
import com.lightbend.lagom.javadsl.jackson.JacksonSerializerFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Futures
import play.api.libs.json.Format
import play.api.libs.json.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ServiceRegistryInteropSpec extends AnyFlatSpec with Matchers with Futures with BeforeAndAfterAll {
  val system                   = ActorSystem()
  val jacksonSerializerFactory = new JacksonSerializerFactory(system)

  protected override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(actorSystem = system, verifySystemShutdown = true)
  }

  behavior.of("ServiceRegistry serializers")

  it should "should interop between java and scala (RegisteredService)" in {
    val msg = jRegisteredService.of("inventory", URI.create("https://localhost:123/asdf"), Optional.of("https"))
    roundTrip(msg) should be(msg)
  }

  it should "should interop between java and scala when optional fields are empty (RegisteredService)" in {
    val msg = jRegisteredService.of("inventory", URI.create("https://localhost:123/asdf"), Optional.empty[String])
    roundTrip(msg) should be(msg)
  }

  it should "should interop between java and scala (ServiceRegistryService)" in {
    val msg = jServiceRegistryService.of(
      URI.create("https://localhost:123/asdf"),
      Collections.singletonList(ServiceAcl.methodAndPath(Method.GET, "/items"))
    )
    roundTrip(msg) should be(msg)
  }

  it should "should interop between java and scala when optional fields are empty (ServiceRegistryService)" in {
    val msg = jServiceRegistryService.of(URI.create("https://localhost:123/asdf"), Collections.emptyList[ServiceAcl])
    roundTrip(msg) should be(msg)
  }

  private def roundTrip(input: jServiceRegistryService): jServiceRegistryService = {
    roundTrip(
      input,
      jacksonSerializerFactory.messageSerializerFor[jServiceRegistryService](classOf[jServiceRegistryService]),
      com.lightbend.lagom.scaladsl.playjson.JsonSerializer[sServiceRegistryService].format
    )(sServiceRegistryService.format)
  }

  private def roundTrip(input: jRegisteredService): jRegisteredService = {
    roundTrip(
      input,
      jacksonSerializerFactory.messageSerializerFor[jRegisteredService](classOf[jRegisteredService]),
      com.lightbend.lagom.scaladsl.playjson.JsonSerializer[sRegisteredService].format
    )(sRegisteredService.format)
  }

  private def roundTrip[J, S](
      input: J,
      jacksonSerializer: StrictMessageSerializer[J],
      playJsonFormatter: Format[S]
  )(implicit format: Format[S]): J = {
    val byteString: ByteString = jacksonSerializer.serializerForRequest().serialize(input)
    val scalaValue: S          = playJsonFormatter.reads(Json.parse(byteString.toArray)).get
    val str: String            = playJsonFormatter.writes(scalaValue).toString()
    val jacksonDeserializer: MessageSerializer.NegotiatedDeserializer[J, ByteString] =
      jacksonSerializer.deserializer(
        new MessageProtocol(Optional.of("application/json"), Optional.empty[String], Optional.empty[String])
      )
    jacksonDeserializer.deserialize(ByteString(str))
  }
}
