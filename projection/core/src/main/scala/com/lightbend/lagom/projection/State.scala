/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.projection

import java.util.Objects

import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates

import scala.util.control.NoStackTrace

@ApiMayChange
sealed trait Status  extends ProjectionSerializable
sealed trait Stopped extends Status
case object Stopped  extends Stopped
sealed trait Started extends Status
case object Started  extends Started

@ApiMayChange
final class Worker private (
    val tagName: String,
    val key: String,
    val requestedStatus: Status,
    val observedStatus: Status,
) extends ProjectionSerializable {
  override def equals(other: Any): Boolean =
    this.eq(other.asInstanceOf[AnyRef]) || (other match {
      case that: Worker =>
        tagName == that.tagName &&
          key == that.key &&
          requestedStatus == that.requestedStatus &&
          observedStatus == that.observedStatus
      case _ => false
    })
  override def hashCode(): Int = Objects.hash(tagName, key, requestedStatus, observedStatus)
}

object Worker {
  def apply(tagName: String, key: String, requestedStatus: Status, observedStatus: Status) =
    new Worker(tagName, key, requestedStatus, observedStatus)
}

@ApiMayChange
final class Projection private (val name: String, val workers: Seq[Worker]) extends ProjectionSerializable

object Projection {
  def apply(name: String, workers: Seq[Worker]) = new Projection(name, workers)
}

@ApiMayChange
final class State(val projections: Seq[Projection]) extends ProjectionSerializable {
  def findProjection(projectionName: String): Option[Projection] =
    projections.find(_.name == projectionName)

  def findWorker(workerKey: String): Option[Worker] =
    projections.flatMap(_.workers).find(_.key == workerKey)
}

object State {

  type WorkerKey = String
  private[lagom] def fromReplicatedData(
      nameIndex: Map[WorkerKey, WorkerCoordinates],
      requestedStatusLocalCopy: Map[WorkerKey, Status],
      observedStatusLocalCopy: Map[WorkerKey, Status],
      defaultRequested: Status,
      defaultObserved: Status
  ): State = {

    val workers: Map[String, Seq[Worker]] = nameIndex
      .map {
        case (workerKey, coordinates) =>
          val w = Worker(
            coordinates.tagName,
            coordinates.asKey,
            requestedStatusLocalCopy.getOrElse(workerKey, defaultRequested),
            observedStatusLocalCopy.getOrElse(workerKey, defaultObserved)
          )
          w -> coordinates.projectionName
      }
      .groupBy(_._2)
      .mapValues(_.keys.toSeq)
      .toMap

    val projections = workers.map {
      case (projectionName, ws) => Projection(projectionName, ws)
    }.toSeq
    new State(projections)
  }

}

@ApiMayChange
final class ProjectionNotFound(val projectionName: String)
    extends RuntimeException(s"Projection $projectionName is not registered")
    with NoStackTrace

@ApiMayChange
final class ProjectionWorkerNotFound(val workerName: String)
    extends RuntimeException(s"Projection $workerName is not registered")
    with NoStackTrace
