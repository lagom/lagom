/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.projection

import java.util.Objects

import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.ProjectionName
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

@ApiMayChange
sealed trait Status  extends ProjectionSerializable
sealed trait Stopped extends Status
sealed trait Started extends Status
case object Started  extends Started { def getInstance: Started = this }
case object Stopped  extends Stopped { def getInstance: Stopped = this }

@ApiMayChange
final class Worker private (
    @BeanProperty val tagName: String,
    @BeanProperty val key: String,
    @BeanProperty val requestedStatus: Status,
    @BeanProperty val observedStatus: Status,
) extends ProjectionSerializable {
  override def equals(other: Any) =
    this.eq(other.asInstanceOf[AnyRef]) || (other match {
      case that: Worker =>
        Objects.equals(tagName, that.tagName) &&
          Objects.equals(key, that.key) &&
          Objects.equals(requestedStatus, that.requestedStatus) &&
          Objects.equals(observedStatus, that.observedStatus)
      case _ => false
    })
  override def hashCode = Objects.hash(tagName, key, requestedStatus, observedStatus)

  override def toString =
    s"Worker(tagName=$tagName, key=$key, requestedStatus=$requestedStatus, observedStatus=$observedStatus)"
}

object Worker {
  def apply(tagName: String, key: String, requestedStatus: Status, observedStatus: Status) =
    new Worker(tagName, key, requestedStatus, observedStatus)
}

@ApiMayChange
final class Projection private (val name: String, val workers: Seq[Worker]) extends ProjectionSerializable {
  override def toString: String =
    s"Projection(name=$name, workers=Seq(${workers.mkString(", ")}))"
}

object Projection {
  def apply(name: String, workers: Seq[Worker]) = new Projection(name, workers)
}

@ApiMayChange
final class State(val projections: Seq[Projection]) extends ProjectionSerializable {
  def findProjection(projectionName: String): Option[Projection] =
    projections.find(_.name == projectionName)

  def findWorker(workerKey: String): Option[Worker] =
    projections.flatMap(_.workers).find(_.key == workerKey)

  def getProjections: java.util.List[Projection] = projections.asJava

  override def equals(other: Any): Boolean =
    this.eq(other.asInstanceOf[AnyRef]) || (other match {
      case that: State => Objects.equals(projections, that.projections)
      case _           => false
    })

  override def hashCode = Objects.hash(projections)

  override def toString = s"State(projections=$projections)"
}

object State {

  private[lagom] def fromReplicatedData(
      nameIndex: Map[ProjectionName, Set[WorkerCoordinates]],
      requestedStatusLocalCopy: Map[WorkerCoordinates, Status],
      observedStatusLocalCopy: Map[WorkerCoordinates, Status],
      defaultRequested: Status,
      defaultObserved: Status
  ): State = {

    val projections = nameIndex.map {
      case (projectionName, coordinatesSet) =>
        val workersSet = coordinatesSet.map { coordinates =>
          Worker(
            coordinates.tagName,
            coordinates.asKey,
            requestedStatusLocalCopy.getOrElse(coordinates, defaultRequested),
            observedStatusLocalCopy.getOrElse(coordinates, defaultObserved)
          )
        }
        Projection(projectionName, workersSet.toSeq)
    }.toSeq
    new State(projections)
  }

}
