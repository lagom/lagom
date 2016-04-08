/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.persistence

import java.util.Optional
import java.util.UUID
import scala.concurrent.duration._
import akka.japi.Pair
import akka.stream.scaladsl
import akka.NotUsed
import akka.Done
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * At system startup all [[PersistentEntity]] classes must be registered here
 * with [[PersistentEntityRegistry#register]].
 *
 * Later, [[PersistentEntityRef]] can be retrieved with [[PersistentEntityRegistry#refFor]].
 * Commands are sent to a [[PersistentEntity]] using a `PersistentEntityRef`.
 */
trait CorePersistentEntityRegistry {

  /**
   * At system startup all [[PersistentEntity]] classes must be registered
   * with this method.
   */
  def register[C, E, S, Entity <: CorePersistentEntity[C, E, S]: ClassTag](entityFactory: () => Entity): Unit

  /**
   * Retrieve a [[PersistentEntityRef]] for a given [[PersistentEntity]] class
   * and identifier. Commands are sent to a `PersistentEntity` using a `PersistentEntityRef`.
   */
  def refFor[C](entityClass: Class[_ <: CorePersistentEntity[C, _, _]], entityId: String): CorePersistentEntityRef[C]

  /**
   * A stream of the persistent events that have the given `aggregateTag`, e.g.
   * all persistent events of all `Order` entities.
   */
  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Optional[UUID]
  ): scaladsl.Source[Pair[Event, UUID], NotUsed]

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
