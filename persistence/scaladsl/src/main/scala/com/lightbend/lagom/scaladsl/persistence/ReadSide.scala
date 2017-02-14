/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

/**
 * The Lagom read-side registry.
 *
 * Handles the management of read-sides.
 */
trait ReadSide {

  /**
   * Register a read-side processor with Lagom.
   *
   * * The `processorFactory` will be called when a new processor instance is to be created.
   * That will happen in another thread, so the `processorFactory` must be thread-safe, e.g.
   * not close over shared mutable state that is not thread-safe.
   */
  def register[Event <: AggregateEvent[Event]](processorFactory: => ReadSideProcessor[Event]): Unit

}
