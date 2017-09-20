/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import akka.actor.ActorSystem
import akka.pattern.CircuitBreakerOpenException
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Futures
import org.scalatest.{ AsyncFlatSpec, BeforeAndAfterAll, Matchers }

import scala.concurrent.Future

/**
 *
 */
class CircuitBreakersPanelInternalSpec
  extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with Futures {

  val actorSystem = ActorSystem("CircuitBreakersPanelInternalSpec")

  override def afterAll() = {
    actorSystem.terminate()
  }

  behavior of "CircuitBreakersPanelInternal"

  it should "keep the circuit closed on whitelisted exceptions" in {
    val fakeExceptionName = new FakeException("").getClass.getName
    val whitelist = Array(fakeExceptionName)

    // This CircuitBreakersPanelInternal has 'FakeException' whitelisted so when it's thrown on
    // the 2nd step it won't open the circuit.
    // NOTE the panel is configured to trip on a single exception (see config below)
    val panel: CircuitBreakersPanelInternal = panelWith(whitelist)

    val actual: Future[String] = for {
      _ <- successfulCall(panel, "123")
      _ <- failedCall(panel, new FakeException("boo"))
      x <- successfulCall(panel, "456")
    } yield x

    actual.map { result =>
      result should be("456")
    }
  }

  it should "open the circuit when the exception is not whitelisted" in {
    val whitelist = Array.empty[String]

    // This CircuitBreakersPanelInternal has nothing whitelisted so when a FakeException
    // is thrown on the 2nd step it will open. That will cause the third call to fail
    // which is what we expect.
    // NOTE the panel is configured to trip on a single exception (see config below)
    val panel: CircuitBreakersPanelInternal = panelWith(whitelist)

    // Expect a CircuitBreakerOpenException
    recoverToSucceededIf[CircuitBreakerOpenException] {
      for {
        _ <- successfulCall(panel, "123")
        _ <- failedCall(panel, new FakeException("boo"))
        x <- successfulCall(panel, "456")
      } yield x
    }
  }

  // ---------------------------------------------------------

  private def successfulCall(panel: CircuitBreakersPanelInternal, mockedResponse: String) = {
    panel.withCircuitBreaker("cb")(Future.successful(mockedResponse))
  }

  private def failedCall(panel: CircuitBreakersPanelInternal, failure: Exception) = {
    panel
      .withCircuitBreaker("cb")(Future.failed(failure))
      .recover {
        case _ => Future.successful("We expect a Failure but we must capture the exception thrown to move forward with the test.")
      }
  }

  private def panelWith(whitelist: Array[String]) = {
    val config = configWithWhiteList(whitelist: _*)
    val cbConfig: CircuitBreakerConfig = new CircuitBreakerConfig(config)
    val metricsProvider: CircuitBreakerMetricsProvider = new CircuitBreakerMetricsProviderImpl(actorSystem)
    new CircuitBreakersPanelInternal(actorSystem, cbConfig, metricsProvider)
  }

  // This configuration is prepared for the tests so that it opens the Circuit Breaker
  // after a single failure.
  private def configWithWhiteList(whitelistedExceptions: String*) = ConfigFactory.parseString(
    s"""
       |lagom.circuit-breaker {
       |  default {
       |
       |    ## Set failures to '1' so a single exception trips the breaker.
       |    max-failures = 1
       |
       |    exception-whitelist = [${whitelistedExceptions.mkString(",")}]
       |
       |
       |    enabled = on
       |    call-timeout = 10s
       |    reset-timeout = 15s
       |  }
       |}
       |#//#circuit-breaker-default
    """.stripMargin
  )

}

class FakeException(msg: String) extends RuntimeException(msg)
