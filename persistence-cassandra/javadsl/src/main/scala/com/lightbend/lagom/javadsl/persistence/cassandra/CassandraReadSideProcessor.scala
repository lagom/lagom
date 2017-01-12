/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.function.BiFunction
import java.util.{ Collections, Optional, UUID, List => JList }

import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventTag }

/**
 * Consume events produced by [[com.lightbend.lagom.javadsl.persistence.PersistentEntity]]
 * instances and update one or more tables in Cassandra that are optimized for queries.
 * The events belong to a [[com.lightbend.lagom.javadsl.persistence.AggregateEventTag]], e.g. all
 * persistent events of all `Order` entities.
 */
@deprecated("Use ReadSideProcessor instead with CassandraReadSide builder", "1.2.0")
abstract class CassandraReadSideProcessor[Event <: AggregateEvent[Event]] {

  case class EventHandlers(handlers: Map[Class[_ <: Event], BiFunction[_ <: Event, UUID, CompletionStage[JList[BoundStatement]]]])

  /**
   * Mutable builder for defining event handlers.
   */
  class EventHandlersBuilder {
    private var handlers: Map[Class[_ <: Event], BiFunction[_ <: Event, UUID, CompletionStage[JList[BoundStatement]]]] = Map.empty

    /**
     * Define the event handler that will be used for events of a given class.
     */
    def setEventHandler[E <: Event](eventClass: Class[E], handler: BiFunction[E, UUID, CompletionStage[JList[BoundStatement]]]): Unit =
      handlers = handlers.updated(eventClass, handler)

    /**
     * When all event handlers have been defined the immutable
     * `ReadSideHandler` is created with this method.
     */
    def build(): EventHandlers = new EventHandlers(handlers)
  }

  /**
   * The processed events belong to a [[com.lightbend.lagom.javadsl.persistence.AggregateEventTag]]
   * that is specified by this method, e.g. all persistent events of all `Order` entities.
   */
  def aggregateTag: AggregateEventTag[Event]

  /**
   * First you must tell where in the event stream the processing should start,
   * i.e. return the offset. The current offset is a parameter to the event
   * handler for each event and it should typically be stored so that it can be
   * restored with a `select` statement here. Use the [[CassandraSession]]
   * to get the stored offset.
   *
   * Other things that is typically performed in this method is to create
   * prepared statements that are later used when processing the events.
   * Use [[CassandraSession#prepare]] to create the prepared statements.
   *
   * Return [[#noOffset]] if you want to processes all events, e.g. when
   * starting the first time or if the number of events are known to be small
   * enough to processes all events.
   */
  def prepare(session: CassandraSession): CompletionStage[Optional[UUID]]

  /**
   * Define the event handlers that are to be used. Use the supplied
   * `builder` to define the event handlers. One handler for each event class.
   * A handler is a `BiFunction` that takes the event and the offset as
   * parameters and returns zero or more bound statements that will be executed
   * before processing next event.
   */
  def defineEventHandlers(builder: EventHandlersBuilder): EventHandlers

  /**
   * Convenience method to create an already completed `CompletionStage`
   * with one `BoundStatement`.
   */
  final def completedStatement(stmt: BoundStatement): CompletionStage[JList[BoundStatement]] =
    CompletableFuture.completedFuture(Collections.singletonList(stmt))

  /**
   * Convenience method to create an already completed `CompletionStage`
   * with several `BoundStatement`.
   */
  final def completedStatements(stmts: JList[BoundStatement]): CompletionStage[JList[BoundStatement]] =
    CompletableFuture.completedFuture(stmts)

  /**
   * Convenience method to create an already completed `CompletionStage`
   * with zero `BoundStatement`.
   */
  final val emptyStatements: CompletionStage[JList[BoundStatement]] =
    CompletableFuture.completedFuture(Collections.emptyList[BoundStatement]())

  final val noOffset: CompletionStage[Optional[UUID]] =
    CompletableFuture.completedFuture(Optional.empty())
}
