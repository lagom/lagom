/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.{ Callable, CompletionStage, ConcurrentHashMap, TimeoutException }
import java.util.function.{ Function => JFunction }
import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import akka.pattern.{ CircuitBreakerOpenException, CircuitBreaker => AkkaCircuitBreaker }
import com.lightbend.lagom.internal.spi.{ CircuitBreakerMetrics, CircuitBreakerMetricsProvider }
import com.typesafe.config.Config
import play.api.Configuration

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object CircuitBreakers {
  private final case class CircuitBreakerHolder(breaker: AkkaCircuitBreaker, metrics: CircuitBreakerMetrics)
}

@Singleton
class CircuitBreakers @Inject() (system: ActorSystem, circuitBreakerConfig: CircuitBreakerConfig,
                                 metricsProvider: CircuitBreakerMetricsProvider) {
  import CircuitBreakers._
  val config = circuitBreakerConfig.config
  private val defaultBreakerConfig = circuitBreakerConfig.default
  private val breakers = new ConcurrentHashMap[String, Option[CircuitBreakerHolder]]

  def withCircuitBreaker[T](id: String)(body: Callable[CompletionStage[T]]): CompletionStage[T] = {
    breaker(id) match {
      case Some(CircuitBreakerHolder(b, metrics)) =>
        val startTime = System.nanoTime()
        def elapsed: Long = System.nanoTime() - startTime
        val result = b.withCircuitBreaker(body.call().toScala)
        result.onComplete {
          case Success(_)                              => metrics.onCallSuccess(elapsed)
          case Failure(_: CircuitBreakerOpenException) => metrics.onCallBreakerOpenFailure()
          case Failure(_: TimeoutException)            => metrics.onCallTimeoutFailure(elapsed)
          case Failure(_)                              => metrics.onCallFailure(elapsed)
        }(system.dispatcher)
        result.toJava
      case None => body.call()
    }
  }

  private val createCircuitBreaker = new JFunction[String, Option[CircuitBreakerHolder]] {
    override def apply(id: String): Option[CircuitBreakerHolder] = {
      val breakerConfig =
        if (config.hasPath(id)) config.getConfig(id).withFallback(defaultBreakerConfig)
        else defaultBreakerConfig
      if (breakerConfig.getBoolean("enabled")) {
        val maxFailures = breakerConfig.getInt("max-failures")
        val callTimeout = breakerConfig.getDuration("call-timeout", MILLISECONDS).millis
        val resetTimeout = breakerConfig.getDuration("reset-timeout", MILLISECONDS).millis
        val breaker = new AkkaCircuitBreaker(system.scheduler, maxFailures, callTimeout, resetTimeout)(system.dispatcher)
        val metrics = metricsProvider.start(id)
        breaker.onClose(metrics.onClose())
        breaker.onOpen(metrics.onOpen())
        breaker.onHalfOpen(metrics.onHalfOpen())
        Some(CircuitBreakerHolder(breaker, metrics))
      } else None
    }
  }

  private def breaker(id: String): Option[CircuitBreakerHolder] =
    breakers.computeIfAbsent(id, createCircuitBreaker)

}

@Singleton
class CircuitBreakerConfig @Inject() (configuration: Configuration) {
  val config: Config = configuration.underlying.getConfig("lagom.circuit-breaker")
  val default: Config = config.getConfig("default")
}
