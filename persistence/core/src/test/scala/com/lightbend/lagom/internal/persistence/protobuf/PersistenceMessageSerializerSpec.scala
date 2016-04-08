/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.protobuf

import java.io.NotSerializableException

import scala.concurrent.duration._

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializationExtension
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.persistence.CommandEnvelope
import com.lightbend.lagom.persistence.CorePersistentEntity
import com.lightbend.lagom.persistence.CorePersistentEntity.InvalidCommandException
import com.lightbend.lagom.persistence.CorePersistentEntity.PersistException
import com.lightbend.lagom.persistence.CorePersistentEntity.UnhandledCommandException
import com.lightbend.lagom.persistence.CorePersistentEntityRef

class PersistenceMessageSerializerSpec extends ActorSystemSpec {

  val serializer = new PersistenceMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  def checkSerialization(obj: AnyRef): Unit = {
    // check that it is configured
    SerializationExtension(system).serializerFor(obj.getClass).getClass should be(classOf[PersistenceMessageSerializer])

    // verify serialization-deserialization round trip
    val blob = serializer.toBinary(obj)
    val obj2 = serializer.fromBinary(blob, serializer.manifest(obj))
    obj2 should be(obj)
  }

  "PersistenceMessageSerializer" must {

    "serialize CommandEnvelope" in {
      checkSerialization(CommandEnvelope("entityId", "payload"))
    }

    "serialize InvalidCommandException" in {
      checkSerialization(InvalidCommandException("wrong"))
    }

    "serialize UnhandledCommandException" in {
      checkSerialization(UnhandledCommandException("unhandled"))
    }

    "serialize PersistException" in {
      checkSerialization(PersistException("not stored"))
    }

    "not serialize PersistentEntityRef" in {
      intercept[NotSerializableException] {
        SerializationExtension(system).serialize(new CorePersistentEntityRef[String]("abc", system.deadLetters, system, 5.seconds)).get
      }
    }
  }

}
