/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.akkaserializer

import java.util.Optional

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializationExtension
import com.lightbend.lagom.javadsl.persistence.{ ActorSystemSpec, CommandEnvelope, TestEntity }

class OptionalSerializerSpec extends ActorSystemSpec {

  val serializer = new OptionalSerializer(system.asInstanceOf[ExtendedActorSystem])

  private def checkSerialization(obj: AnyRef) = {
    SerializationExtension(system).serializerFor(obj.getClass).getClass should be(classOf[OptionalSerializer])
    val bytes = serializer.toBinary(obj)
    val restored = serializer.fromBinary(bytes, serializer.manifest(obj))
    restored should be(obj)
  }

  "OptionalSerializer" must {

    "serialize Optional.empty()" in {
      checkSerialization(Optional.empty)
    }

    "serialize Optional.of(String)" in {
      checkSerialization(Optional.of("1234"))
    }

    "serialize Optional.of(CommandEnvelope)" in {
      checkSerialization(Optional.of(CommandEnvelope("entityId", TestEntity.Add.of("a"))))
    }
  }
}
