/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.projection

import java.util.Objects

import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.ProjectionName
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

/**
 * Super type for a worker status ADT. Possible values are {{{Stopped}}} and {{{Started}}}
 */
@ApiMayChange
sealed trait Status  extends ProjectionSerializable
sealed trait Stopped extends Status
sealed trait Started extends Status

/** See {{{Status}}} */
case object Started extends Started { def getInstance: Started = this }

/** See {{{Status}}} */
case object Stopped extends Stopped { def getInstance: Stopped = this }

/**
 * Metadata of a Worker. The existence of this data doesn't mean the actual worker
 * instance exists. This metadata identifies the tag this worker will consume, a
 * unique identifier (aka the `key`) and both the requested and the observed status.
 *
 * Both the requested and observed status are eventually consistent since this metadata
 * is being replicated across the cluster and may have been edited in other nodes when
 * being read in the local node.
 *
 * @param tagName the tag in the event journal this worker will read. This value is
 *                part of the `WorkerCoordinates`.
 * @param key a unique identifier for this worker. Note the `key` is produced from
 *            the `WorkerCoordinates`
 * @param requestedStatus the user-demanded state of the worker. This value is eventually
 *                        consistent as it's only an in-memory, replicated value (not read
 *                        from a durable storage)
 * @param observedStatus the status of the actual worker as observed by the `ProjectionRegistry`.
 *                       A node in the cluster hosts an actor (the actual Worker actor) which
 *                       may be `Stopped` or `Started`. As that actor spawns or dies, the local
 *                       instance of the `ProjectionRegistry` will observe the actor and share
 *                       the information with the rest of the `ProjectionRegistry` instances
 *                       across the cluster.
 */
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

/**
 * Current status of a projection.
 *
 * @param name unique ID of the projection
 * @param workers list of workers participating on this projection.
 */
@ApiMayChange
final class Projection private (val name: String, val workers: Seq[Worker]) extends ProjectionSerializable {
  override def toString: String =
    s"Projection(name=$name, workers=Seq(${workers.mkString(", ")}))"
}

object Projection {
  def apply(name: String, workers: Seq[Worker]) = new Projection(name, workers)
}

/**
 * The state of a projections registry is a collection of projections with extra data indicating the
 * name of the projection, and details about its workers. Note that many projections may operate over
 * the same journal. Each worker includes information about the particular tagName it is tracking. Note
 * that multiple workers may track the same tagName because each worker is part of a different projection.
 * Each worker also has a key which is unique across the whole cluster. Finally, the data related to a
 * worker that's part of the State includes a requested status and an observed status for the worker.
 *
 * @param projections list of available projections on the internal registry
 */
@ApiMayChange
final class State(val projections: Seq[Projection]) extends ProjectionSerializable {

  /** Java API  */
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
  @InternalApi
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
