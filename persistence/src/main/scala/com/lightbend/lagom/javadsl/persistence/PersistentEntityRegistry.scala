/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.Optional
import java.util.UUID
import scala.concurrent.duration._
import akka.japi.Pair
import akka.stream.javadsl
import akka.NotUsed
import java.util.concurrent.CompletionStage
import akka.Done

/**
 * At system startup all [[PersistentEntity]] classes must be registered here
 * with [[PersistentEntityRegistry#register]].
 *
 * Later, [[PersistentEntityRef]] can be retrieved with [[PersistentEntityRegistry#refFor]].
 * Commands are sent to a [[PersistentEntity]] using a `PersistentEntityRef`.
 */
trait PersistentEntityRegistry {

  /**
   * At system startup all [[PersistentEntity]] classes must be registered
   * with this method.
   */
  def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit

  /**
   * Retrieve a [[PersistentEntityRef]] for a given [[PersistentEntity]] class
   * and identifier. Commands are sent to a `PersistentEntity` using a `PersistentEntityRef`.
   */
  def refFor[C](entityClass: Class[_ <: PersistentEntity[C, _, _]], entityId: String): PersistentEntityRef[C]

  /**
   * A stream of the persistent events that have the given `aggregateTag`, e.g.
   * all persistent events of all `Order` entities.
   */
  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Optional[UUID]
  ): javadsl.Source[Pair[Event, UUID], NotUsed]

  /**
   * Gracefully stop the persistent entities and leave the cluster.
   * The persistent entities will be started on another node when
   * new messages are sent to them.
   *
   * @return the `CompletionStage` is completed when the node has been
   *   removed from the cluster
   */
  def gracefulShutdown(timeout: FiniteDuration): CompletionStage[Done]

}
