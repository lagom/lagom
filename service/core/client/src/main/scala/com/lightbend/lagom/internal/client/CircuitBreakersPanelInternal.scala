/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.function.{ Function => JFunction }

import akka.actor.ActorSystem
import akka.pattern.CircuitBreakerOpenException
import akka.pattern.{ CircuitBreaker => AkkaCircuitBreaker }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetrics
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.typesafe.config.Config
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * This is the internal CircuitBreakersPanel implementation.
 * Javadsl and Scaladsl delegates to this one.
 */
private[lagom] class CircuitBreakersPanelInternal(
    system: ActorSystem,
    circuitBreakerConfig: CircuitBreakerConfig,
    metricsProvider: CircuitBreakerMetricsProvider
) {
  private final case class CircuitBreakerHolder(
      breaker: AkkaCircuitBreaker,
      metrics: CircuitBreakerMetrics,
      failedCallDefinition: Try[_] => Boolean
  )

  private lazy val config               = circuitBreakerConfig.config
  private lazy val defaultBreakerConfig = circuitBreakerConfig.default

  private val breakers = new ConcurrentHashMap[String, Option[CircuitBreakerHolder]]

  def withCircuitBreaker[T](id: String)(body: => Future[T]): Future[T] = {
    breaker(id) match {
      case Some(CircuitBreakerHolder(b, metrics, failedCallDefinition)) =>
        val startTime = System.nanoTime()

        def elapsed: Long = System.nanoTime() - startTime

        val result: Future[T] = b.withCircuitBreaker(body, failedCallDefinition)
        result.onComplete {
          case Success(_)                                                   => metrics.onCallSuccess(elapsed)
          case failure @ Failure(_) if !failedCallDefinition.apply(failure) => metrics.onCallSuccess(elapsed)
          case Failure(_: CircuitBreakerOpenException)                      => metrics.onCallBreakerOpenFailure()
          case Failure(_: TimeoutException)                                 => metrics.onCallTimeoutFailure(elapsed)
          case Failure(_)                                                   => metrics.onCallFailure(elapsed)
        }(system.dispatcher)
        result
      case None => body
    }
  }

  private val createCircuitBreaker = new JFunction[String, Option[CircuitBreakerHolder]] {
    private val allExceptionAsFailure: Try[_] => Boolean = {
      case _: Success[_] => false
      case _             => true
    }

    private def ignoredException(ex: Any, whitelist: Set[String]): Boolean = ex match {
      case ce: CompletionException => ce.getCause != null && whitelist.contains(ce.getCause.getClass.getName)
      case _                       => whitelist.contains(ex.getClass.getName)
    }

    private def failureDefinition(whitelist: Set[String]): Try[_] => Boolean = {
      case _: Success[_]                                => false
      case Failure(t) if ignoredException(t, whitelist) => false
      case _                                            => true
    }

    override def apply(id: String): Option[CircuitBreakerHolder] = {
      val breakerConfig =
        if (config.hasPath(id)) config.getConfig(id).withFallback(defaultBreakerConfig)
        else defaultBreakerConfig

      if (breakerConfig.getBoolean("enabled")) {
        val maxFailures  = breakerConfig.getInt("max-failures")
        val callTimeout  = breakerConfig.getDuration("call-timeout", MILLISECONDS).millis
        val resetTimeout = breakerConfig.getDuration("reset-timeout", MILLISECONDS).millis

        import scala.collection.JavaConverters.asScalaBufferConverter
        val exceptionWhitelist: Set[String] = breakerConfig.getStringList("exception-whitelist").asScala.toSet

        val definitionOfFailure =
          if (exceptionWhitelist.isEmpty) allExceptionAsFailure else failureDefinition(exceptionWhitelist)

        val breaker =
          new AkkaCircuitBreaker(system.scheduler, maxFailures, callTimeout, resetTimeout)(system.dispatcher)
        val metrics = metricsProvider.start(id)

        breaker.onClose(metrics.onClose())
        breaker.onOpen(metrics.onOpen())
        breaker.onHalfOpen(metrics.onHalfOpen())

        Some(CircuitBreakerHolder(breaker, metrics, definitionOfFailure))
      } else None
    }
  }

  private def breaker(id: String): Option[CircuitBreakerHolder] =
    breakers.computeIfAbsent(id, createCircuitBreaker)
}

@Singleton
class CircuitBreakerConfig @Inject() (val configuration: Config) {
  val config: Config  = configuration.getConfig("lagom.circuit-breaker")
  val default: Config = config.getConfig("default")
}
