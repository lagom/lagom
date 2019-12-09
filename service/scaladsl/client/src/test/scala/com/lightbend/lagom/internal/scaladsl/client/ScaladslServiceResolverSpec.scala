/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.client

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.CircuitBreaker
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.client.TestServiceClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScaladslServiceResolverSpec extends AnyFlatSpec with Matchers {
  behavior.of("ScaladslServiceResolver")

  it should "setup circuit-breakers for all method calls using default values when nothing is specified" in {
    assertCircuitBreaking(TestServiceClient.implement[Unspecified], CircuitBreaker.PerNode)
  }

  it should "setup circuit-breakers for all method calls using descriptor value when only descriptor's CB is specified" in {
    assertCircuitBreaking(TestServiceClient.implement[General], CircuitBreaker.identifiedBy("general-cb"))
  }

  it should "setup circuit-breakers with each specific CB when each call has a CB described" in {
    assertCircuitBreaking(TestServiceClient.implement[PerCall], CircuitBreaker.identifiedBy("one-cb"))
  }

  // --------------------------------------------------------------------------------------------
  private def assertCircuitBreaking(service: Service, expected: CircuitBreaker) = {
    val resolved = new ScaladslServiceResolver(DefaultExceptionSerializer.Unresolved).resolve(service.descriptor)
    resolved.calls.head.circuitBreaker should be(Some(expected))
  }

  trait Unspecified extends Service {
    import Service._

    def one: ServiceCall[NotUsed, NotUsed]

    override def descriptor: Descriptor = {
      named("Unspecified")
        .withCalls(
          namedCall("one", one)
        )
    }
  }

  trait General extends Service {
    import Service._

    def one: ServiceCall[NotUsed, NotUsed]

    override def descriptor: Descriptor = {
      named("Unspecified")
        .withCalls(
          namedCall("one", one)
        )
        .withCircuitBreaker(CircuitBreaker.identifiedBy("general-cb"))
    }
  }

  trait PerCall extends Service {
    import Service._

    def one: ServiceCall[NotUsed, NotUsed]

    override def descriptor: Descriptor = {
      named("Unspecified")
        .withCalls(
          namedCall("one", one)
            .withCircuitBreaker(CircuitBreaker.identifiedBy("one-cb")) // overwrites default.
        )
        .withCircuitBreaker(CircuitBreaker.identifiedBy("general-cb"))
    }
  }
}
