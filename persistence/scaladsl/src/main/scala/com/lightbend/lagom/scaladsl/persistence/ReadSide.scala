/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
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
   * @param processorClass The read-side processor class to register. It will be instantiated using Guice, once for
   *                       every shard that runs it. Typically it should not be a singleton.
   */
  def register[Event <: AggregateEvent[Event]](processorClass: Class[_ <: ReadSideProcessor[Event]]): Unit

}
