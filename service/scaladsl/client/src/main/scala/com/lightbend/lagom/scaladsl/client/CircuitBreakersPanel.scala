/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import com.lightbend.lagom.internal.scaladsl.client.CircuitBreakersPanelImpl

import scala.concurrent.Future

/**
 * A CircuitBreakersPanel is a central point collecting all circuit breakers in Lagom.
 *
 * Calls to remote services can make use of this facility in order to add circuit breaking capabilities to it.
 */
trait CircuitBreakersPanel {
  def withCircuitBreaker[T](id: String)(body: => Future[T]): Future[T]
}

