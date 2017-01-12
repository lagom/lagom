/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import java.sql.Connection

import com.lightbend.lagom.scaladsl.persistence._

import scala.reflect.ClassTag

/**
 * JDBC read side support.
 *
 * This should be used to build and register a read side processor.
 *
 * All callbacks are executed in a transaction and are automatically committed or rollback based on whether they fail
 * or succeed.
 *
 * Offsets are automatically handled.
 */
trait JdbcReadSide {
  /**
   * Create a builder for a Cassandra read side event handler.
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
     * Set a global prepare callback.
     *
     * @param callback The callback.
     * @return This builder for fluent invocation.
     * @see ReadSideHandler#globalPrepare()
     */
    def setGlobalPrepare(callback: Connection => Unit): ReadSideHandlerBuilder[Event]

    /**
     * Set a prepare callback.
     *
     * @param callback The callback.
     * @return This builder for fluent invocation.
     * @see ReadSideHandler#prepare(AggregateEventTag)
     */
    def setPrepare(callback: (Connection, AggregateEventTag[Event]) => Unit): ReadSideHandlerBuilder[Event]

    /**
     * Define the event handler that will be used for events of a given class.
     *
     * @param handler The function to handle the events.
     * @tparam E The event class to handle.
     * @return This builder for fluent invocation
     */
    def setEventHandler[E <: Event: ClassTag](handler: (Connection, EventStreamElement[E]) => Unit): ReadSideHandlerBuilder[Event]

    /**
     * Build the read side handler.
     *
     * @return The read side handler.
     */
    def build(): ReadSideProcessor.ReadSideHandler[Event]
  }

}
