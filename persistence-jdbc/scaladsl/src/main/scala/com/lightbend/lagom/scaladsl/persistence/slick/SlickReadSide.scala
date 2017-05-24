/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import slick.dbio.{ DBIOAction, NoStream }
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend.Database
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

/**
 * Slick read side support.
 *
 * This should be used to build and register a read side processor.
 *
 * All callbacks are executed in a transaction and are automatically committed or rollback based on whether they succeed or fail.
 *
 * Offsets are automatically handled.
 */
trait SlickReadSide {

  implicit protected val executionContext: ExecutionContext

  /**
   * Create a builder for a Slick read side event handler.
   *
   * @param readSideId An identifier for this read side. This will be used to store offsets in the offset store.
   * @return The builder.
   */
  def builder[Event <: AggregateEvent[Event]](readSideId: String): ReadSideHandlerBuilder[Event]

  /**
   * Builder for the handler.
   */
  trait ReadSideHandlerBuilder[Event <: AggregateEvent[Event]] {

    /**
     * Set a global prepare Database I/O Action.
     *
     * @param dbio The action.
     * @return This builder for fluent invocation.
     * @see ReadSideHandler#globalPrepare()
     */
    def setGlobalPrepare(dbio: DBIOAction[Any, _, _]): ReadSideHandlerBuilder[Event]

    /**
     * Set a prepare Database I/O Action.
     *
     * @param dbio The callback.
     * @return This builder for fluent invocation.
     * @see ReadSideHandler#prepare(AggregateEventTag)
     */
    def setPrepare(dbio: (AggregateEventTag[Event]) => DBIOAction[Any, NoStream, Nothing]): ReadSideHandlerBuilder[Event]

    /**
     * Define the event handler that will be used for events of a given class.
     *
     * @param handler The function to handle the events.
     * @tparam E The event class to handle.
     * @return This builder for fluent invocation
     */
    def setEventHandler[E <: Event: ClassTag](handler: (EventStreamElement[E]) => DBIOAction[Any, NoStream, Nothing]): ReadSideHandlerBuilder[Event]

    /**
     * Build the read side handler.
     *
     * @return The read side handler.
     */
    def build(): ReadSideProcessor.ReadSideHandler[Event]
  }
}
