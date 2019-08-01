/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.util.ByteString
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.Worker
import com.typesafe.config.ConfigFactory
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import com.lightbend.lagom.internal.projection.protobuf.msg.{ ProjectionMessages => pm }
import com.lightbend.lagom.projection.Projection
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped

object ProjectionMessagesSerializerSpec {
  def actorSystem(): ActorSystem = {
    val config = ConfigFactory.defaultReference()
    ActorSystem(classOf[ProjectionMessagesSerializerSpec].getSimpleName, config)
  }
}

class ProjectionMessagesSerializerSpec
    extends TestKit(ProjectionMessagesSerializerSpec.actorSystem())
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with TypeCheckedTripleEquals
    with ImplicitSender {

  import ProjectionMessageSerializer._

  val projectionMessageSerializer = new ProjectionMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  "ProjectionMessageSerializer" must {

    "serialize status Started" in {
      val bytes = projectionMessageSerializer.toBinary(Started)
      ByteString(bytes).utf8String should be("Started")
    }

    "serialize status Stopped" in {
      val bytes = projectionMessageSerializer.toBinary(Stopped)
      ByteString(bytes).utf8String should be("Stopped")
    }

    "serialize Worker" in {
      val worker = Worker("test-tag-name", "test-key-1", Started, Stopped)
      val bytes  = projectionMessageSerializer.toBinary(worker)

      val protoWorker = pm.Worker.parseFrom(bytes)

      protoWorker.getTagName should be("test-tag-name")
      protoWorker.getKey should be("test-key-1")
      protoWorker.getRequestedStatus should be("Started")
      protoWorker.getObservedStatus should be("Stopped")
    }

    "serialize Projection" in {
      val workers    = Range(0, 10).map(n => Worker(s"test-tag-name-${n}", s"test-key-$n", Started, Started))
      val projection = Projection("projection-name", workers)
      val bytes      = projectionMessageSerializer.toBinary(projection)

      val protoProjection = pm.Projection.parseFrom(bytes)

      protoProjection.getName should be("projection-name")
      protoProjection.getWorkersCount should be(10)
      protoProjection.getWorkers(0).getKey should be("test-key-0")
    }

    "serialize State" in {
      val workers     = Range(0, 10).map(n => Worker(s"test-tag-name-${n}", s"test-key-$n", Started, Started))
      val projections = Seq(Projection("projection-name", workers))
      val state       = State(projections)
      val bytes       = projectionMessageSerializer.toBinary(state)

      val protoState = pm.State.parseFrom(bytes)

      protoState.getProjectionsCount should be(1)
      protoState.getProjections(0).getName should be("projection-name")
      protoState.getProjections(0).getWorkersCount should be(10)
      protoState.getProjections(0).getWorkers(0).getKey should be("test-key-0")
    }

    "serialize WorkerCoordinates" in {
      val workerCoordinates = WorkerCoordinates("projection-name", "test-tag-name")
      val bytes             = projectionMessageSerializer.toBinary(workerCoordinates)

      val protoCoordinates = pm.WorkerCoordinates.parseFrom(bytes)

      protoCoordinates.getProjectionName should be("projection-name")
      protoCoordinates.getTagName should be("test-tag-name")
    }

    "deserialize Status Started" in {
      val bytes  = ByteString("Started").toArray[Byte]
      val status = projectionMessageSerializer.fromBinary(bytes, manifest = StatusManifest).asInstanceOf[Status]
      status should be(Started)
    }

    "deserialize Status Stopped" in {
      val bytes  = ByteString("Stopped").toArray[Byte]
      val status = projectionMessageSerializer.fromBinary(bytes, manifest = StatusManifest).asInstanceOf[Status]
      status should be(Stopped)
    }

    "deserialize Worker" in {
      val bytes = pm.Worker
        .newBuilder()
        .setTagName("test-tag-name")
        .setKey("test-key")
        .setRequestedStatus("Stopped")
        .setObservedStatus("Started")
        .build()
        .toByteArray

      val worker = projectionMessageSerializer.fromBinary(bytes, manifest = WorkerManifest).asInstanceOf[Worker]
      worker.tagName should be("test-tag-name")
      worker.key should be("test-key")
      worker.requestedStatus should be(Stopped)
      worker.observedStatus should be(Started)
    }

    "deserialize Projection" in {
      val protoWorker = pm.Worker
        .newBuilder()
        .setTagName("test-tag-name")
        .setKey("test-key")
        .setRequestedStatus("Stopped")
        .setObservedStatus("Started")
        .build()

      val bytes = pm.Projection
        .newBuilder()
        .setName("projection-test")
        .addWorkers(protoWorker)
        .build()
        .toByteArray

      val projection =
        projectionMessageSerializer.fromBinary(bytes, manifest = ProjectionManifest).asInstanceOf[Projection]

      projection.name should be("projection-test")
      projection.workers.length should be(1)
      projection.workers.head.tagName should be("test-tag-name")
    }

    "deserialize State" in {
      val protoWorker = pm.Worker
        .newBuilder()
        .setTagName("test-tag-name")
        .setKey("test-key")
        .setRequestedStatus("Stopped")
        .setObservedStatus("Started")
        .build()

      val protoProjection = pm.Projection
        .newBuilder()
        .setName("projection-test")
        .addWorkers(protoWorker)
        .build()

      val bytes = pm.State
        .newBuilder()
        .addProjections(protoProjection)
        .build()
        .toByteArray

      val state = projectionMessageSerializer.fromBinary(bytes, manifest = StateManifest).asInstanceOf[State]

      state.projections.length should be(1)
      state.projections.head.name should be("projection-test")
      state.projections.head.workers.head.tagName should be("test-tag-name")
    }

    "deserialize WorkerCoordinates" in {
      val bytes = pm.WorkerCoordinates
        .newBuilder()
        .setProjectionName("projection-name")
        .setTagName("test-tag-name")
        .build()
        .toByteArray

      val workerCoordinates = projectionMessageSerializer
        .fromBinary(bytes, manifest = WorkerCoordinatesManifest)
        .asInstanceOf[WorkerCoordinates]

      workerCoordinates.tagName should be("test-tag-name")
      workerCoordinates.projectionName should be("projection-name")
    }

    "fail to serialize an unknown type" in {
      assertThrows[IllegalArgumentException] {
        projectionMessageSerializer.toBinary("Strings are not supported")
      }
    }

    "fail to deserialize with the wrong manifest" in {
      assertThrows[IllegalArgumentException] {
        // Valid data
        val bytes = ByteString("Stopped").toArray[Byte]
        // But invalid manifest
        projectionMessageSerializer.fromBinary(bytes, manifest = "WRONG-MANIFEST").asInstanceOf[Status]
      }
    }
  }
}
