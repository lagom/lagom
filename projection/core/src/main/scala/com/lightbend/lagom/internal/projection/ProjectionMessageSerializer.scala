/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializerWithStringManifest
import akka.serialization.BaseSerializer
import akka.util.ByteString
import com.lightbend.lagom.projection.Projection
import com.lightbend.lagom.projection.State
import com.lightbend.lagom.projection.Worker
import com.lightbend.lagom.internal.projection.protobuf.msg.{ ProjectionMessages => pm }
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.projection.Status
import com.lightbend.lagom.projection.Stopped

import scala.collection.JavaConverters._

private[lagom] class ProjectionMessageSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  val WorkerManifest     = "W"
  val ProjectionManifest = "P"
  val StatusManifest     = "S"
  val StateManifest      = "ST"

  private val fromBinaryMap = collection.immutable.HashMap[String, Array[Byte] => AnyRef](
    WorkerManifest     -> workerFromBinary,
    ProjectionManifest -> projectionFromBinary,
    StatusManifest     -> statusFromBinary,
    StateManifest      -> stateFromBinary
  )

  override def manifest(obj: AnyRef): String = obj match {
    case _: Worker     => WorkerManifest
    case _: Projection => ProjectionManifest
    case _: Status     => StatusManifest
    case _: State      => StateManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case w: Worker     => workerToProto(w).toByteArray
    case p: Projection => projectionToProto(p).toByteArray
    case s: Status     => statusToBinary(s)
    case s: State      => stateToProto(s).toByteArray
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  private def workerToProto(worker: Worker): pm.Worker = {
    val requestedStatus = statusToString(worker.requestedStatus)
    val observedStatus  = statusToString(worker.observedStatus)

    pm.Worker
      .newBuilder()
      .setTagName(worker.tagName)
      .setKey(worker.key)
      .setRequestedStatus(requestedStatus)
      .setObservedStatus(observedStatus)
      .build()
  }

  private def statusToBinary(status: Status): Array[Byte] = statusToByteString(status).toArray[Byte]

  private def statusToString(status: Status): String = statusToByteString(status).utf8String

  private def statusToByteString(status: Status): ByteString = status match {
    case Stopped => ByteString("Stopped")
    case Started => ByteString("Started")
  }

  private def projectionToProto(projection: Projection): pm.Projection = {
    val workers = projection.workers.map(workerToProto).asJava
    pm.Projection
      .newBuilder()
      .setName(projection.name)
      .addAllWorkers(workers)
      .build()
  }

  private def stateToProto(state: State): pm.State = {
    val projections = state.projections.map(projectionToProto).asJava
    pm.State
      .newBuilder()
      .addAllProjections(projections)
      .build()
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    fromBinaryMap.get(manifest) match {
      case Some(f) => f(bytes)
      case None =>
        throw new IllegalArgumentException(
          s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]"
        )
    }

  private def workerFromBinary(bytes: Array[Byte]): Worker =
    workerFromProto(pm.Worker.parseFrom(bytes))

  private def workerFromProto(worker: pm.Worker): Worker = {
    val requestedStatus = statusFromString(worker.getRequestedStatus)
    val observedStatus  = statusFromString(worker.getObservedStatus)

    Worker(worker.getTagName, worker.getKey, requestedStatus, observedStatus)
  }

  private def statusFromString(status: String): Status = statusFromBinary(ByteString.fromString(status))

  private def statusFromBinary(bytes: Array[Byte]): Status = statusFromBinary(ByteString(bytes))

  private def statusFromBinary(bytes: ByteString): Status = bytes.utf8String match {
    case "Stopped" => Stopped
    case "Started" => Started
  }

  private def projectionFromBinary(bytes: Array[Byte]): Projection =
    projectionFromProto(pm.Projection.parseFrom(bytes))

  private def projectionFromProto(projection: pm.Projection): Projection = {
    val workers = projection.getWorkersList.asScala.map(workerFromProto)
    Projection(projection.getName, workers)
  }

  private def stateFromBinary(bytes: Array[Byte]): State =
    stateFromProto(pm.State.parseFrom(bytes))

  private def stateFromProto(state: pm.State): State = {
    val projections = state.getProjectionsList.asScala.map(projectionFromProto)
    State(projections)
  }

}
