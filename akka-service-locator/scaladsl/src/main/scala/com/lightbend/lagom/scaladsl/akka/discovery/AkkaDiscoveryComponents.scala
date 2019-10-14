/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.akka.discovery

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.client.CircuitBreakerComponents

/**
 * Mix this into your application cake to get the Akka Discovery based implementation of the Lagom
 * [[ServiceLocator]].
 */
trait AkkaDiscoveryComponents extends CircuitBreakerComponents {
  lazy val serviceLocator: ServiceLocator =
    new AkkaDiscoveryServiceLocator(circuitBreakersPanel, actorSystem) (executionContext)
}
