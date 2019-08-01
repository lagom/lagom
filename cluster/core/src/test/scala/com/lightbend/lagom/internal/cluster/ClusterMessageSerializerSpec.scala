/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.cluster.protobuf.msg.ClusterMessages.{ EnsureActive => ProtobufEnsureActive }
import com.typesafe.config.ConfigFactory
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

object ClusterMessageSerializerSpec {
  def actorSystem(): ActorSystem = {
    val config = ConfigFactory.defaultReference()
    ActorSystem(classOf[ClusterMessageSerializerSpec].getSimpleName, config)
  }
}

class ClusterMessageSerializerSpec
    extends TestKit(ClusterMessageSerializerSpec.actorSystem())
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with TypeCheckedTripleEquals
    with ImplicitSender {

  val clusterMessageSerializer = new ClusterMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  "ClusterMessageSerializer" must {

    "serialize EnsureActive" in {
      val ensureActive = EnsureActive("entity-1")
      val bytes        = clusterMessageSerializer.toBinary(ensureActive)
      ProtobufEnsureActive.parseFrom(bytes).getEntityId should be("entity-1")
    }

    "deserialize EnsureActive" in {
      val bytes        = ProtobufEnsureActive.newBuilder().setEntityId("entity-2").build().toByteArray
      val ensureActive = clusterMessageSerializer.fromBinary(bytes, "E").asInstanceOf[EnsureActive]
      ensureActive.entityId should be("entity-2")
    }

    "fail to serialize other types" in {
      assertThrows[IllegalArgumentException] {
        clusterMessageSerializer.toBinary("Strings are not supported")
      }
    }

    "fail to deserialize with the wrong manifest" in {
      assertThrows[IllegalArgumentException] {
        val bytes = ProtobufEnsureActive.newBuilder().setEntityId("entity-2").build().toByteArray
        clusterMessageSerializer.fromBinary(bytes, "WRONG-MANIFEST")
      }
    }
  }

  protected override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }
}
