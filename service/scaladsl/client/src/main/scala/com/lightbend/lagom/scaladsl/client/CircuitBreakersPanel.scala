/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import scala.concurrent.Future

/**
 * A CircuitBreakersPanel is a central point collecting all circuit breakers in Lagom.
 *
 * Calls to remote services can make use of this facility in order to add circuit breaking capabilities to it.
 */
trait CircuitBreakersPanel {
  /**
   * Executes `body` in the context of the circuit breaker identified by `id`. Whether `body` is actually invoked is
   * implementation-dependent, but implementations should call it at most once.
   *
   * @param id the unique identifier for the circuit breaker to use (often a service name)
   * @param body effect to (optionally) execute within the context of the circuit breaker. May throw a
   *             [[RuntimeException]] to signal failure.
   * @tparam T the result type
   * @return a future yielding either the same result as `body`, or failing with an implementation-dependent exception
   */
  def withCircuitBreaker[T](id: String)(body: => Future[T]): Future[T]
}

