/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import java.util.UUID

import scala.concurrent.duration._
import akka.stream.scaladsl
import akka.NotUsed
import akka.Done
import scala.concurrent.Future

/**
 * At system startup all [[PersistentEntity]] classes must be registered here
 * with [[PersistentEntityRegistry#register]].
 *
 * Later, [[com.lightbend.lagom.scaladsl.persistence.PersistentEntityRef]] can be
 * retrieved with [[PersistentEntityRegistry#refFor]].
 * Commands are sent to a [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]]
 * using a `PersistentEntityRef`.
 */
trait PersistentEntityRegistry {

  /**
   * At system startup all [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]]
   * classes must be registered with this method.
   */
  def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit

  /**
   * Retrieve a [[com.lightbend.lagom.scaladsl.persistence.PersistentEntityRef]] for a
   * given [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]] class
   * and identifier. Commands are sent to a `PersistentEntity` using a `PersistentEntityRef`.
   */
  def refFor[C](entityClass: Class[_ <: PersistentEntity[C, _, _]], entityId: String): PersistentEntityRef[C]

  /**
   * A stream of the persistent events that have the given `aggregateTag`, e.g.
   * all persistent events of all `Order` entities.
   *
   * The type of the offset is journal dependent, some journals use time-based
   * UUID offsets, while others use sequence numbers. The passed in `fromOffset`
   * must either be [[Offset#NONE]], or an offset that has previously been produced
   * by this journal.
   *
   * @throws IllegalArgumentException If the `fromOffset` type is not supported
   *   by this journal.
   */
  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): scaladsl.Source[(Event, Offset), NotUsed]

  /**
   * Gracefully stop the persistent entities and leave the cluster.
   * The persistent entities will be started on another node when
   * new messages are sent to them.
   *
   * @return the `Future` is completed when the node has been
   *   removed from the cluster
   */
  def gracefulShutdown(timeout: FiniteDuration): Future[Done]

}
