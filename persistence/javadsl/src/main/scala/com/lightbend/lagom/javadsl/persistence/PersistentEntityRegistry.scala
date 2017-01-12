/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
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
import com.lightbend.lagom.javadsl.persistence.Offset.{ Sequence, TimeBasedUUID }

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
  ): javadsl.Source[Pair[Event, Offset], NotUsed]

  /**
   * A stream of the persistent events that have the given `aggregateTag`, e.g.
   * all persistent events of all `Order` entities.
   *
   * This method will only work with journals that support UUID offsets. Journals that
   * produce sequence offsets will fail during stream handling.
   */
  @deprecated("Use eventStream(AggregateEventTag, Offset) instead", "1.2.0")
  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Optional[UUID]
  ): javadsl.Source[Pair[Event, UUID], NotUsed] = {
    val offset = if (fromOffset.isPresent) {
      Offset.timeBasedUUID(fromOffset.get())
    } else Offset.NONE
    eventStream(aggregateTag, offset).asScala.map { pair =>
      val uuid = pair.second match {
        case timeBased: TimeBasedUUID => timeBased.value()
        case sequence: Sequence =>
          // While we *could* translate the sequence number to a time-based UUID, this would be very bad, since the UUID
          // would either be non unique (violating the fundamental aim of UUIDs), or it would change every time the
          // event was loaded. Also, a sequence number is not a timestamp.
          throw new IllegalStateException("Sequence based offset is not supported in a UUID event stream")
      }
      Pair(pair.first, uuid)
    }.asJava
  }

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
