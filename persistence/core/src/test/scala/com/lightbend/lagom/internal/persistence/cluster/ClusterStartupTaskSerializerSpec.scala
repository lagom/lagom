/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cluster

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializationExtension
import com.lightbend.lagom.persistence.ActorSystemSpec

class ClusterStartupTaskSerializerSpec extends ActorSystemSpec {
  val serializer = new ClusterStartupTaskSerializer(system.asInstanceOf[ExtendedActorSystem])

  def checkSerialization(obj: AnyRef): Unit = {
    // check that it is *not* configured for Lagom 1.3
    SerializationExtension(system).serializerFor(obj.getClass).getClass should not be classOf[ClusterStartupTaskSerializer]

    // verify serialization-deserialization round trip
    val blob = serializer.toBinary(obj)
    val obj2 = serializer.fromBinary(blob, serializer.manifest(obj))
    obj2 should be(obj)
  }

  "ClusterStartupTaskSerializerSpec" must {
    "serialize Execute" in {
      checkSerialization(ClusterStartupTaskActor.Execute)
    }
  }
}
