/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.util.function.{ Function => JFunction }
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.pattern.{ CircuitBreaker => AkkaCircuitBreaker }
import akka.pattern.CircuitBreakerOpenException
import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.google.inject.Provides
import com.lightbend.lagom.internal.spi.CircuitBreakerMetrics
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.javadsl.api.Descriptor.CircuitBreakerId
import com.typesafe.config.Config
import javax.inject.Inject
import javax.inject.Singleton

object CircuitBreaker {
  private final case class CircuitBreakerHolder(breaker: AkkaCircuitBreaker, metrics: CircuitBreakerMetrics)
}

@Singleton
class CircuitBreaker @Inject() (system: ActorSystem, circuitBreakerConfig: CircuitBreakerConfig,
                                metricsProvider: CircuitBreakerMetricsProvider) {
  import CircuitBreaker._
  val config = circuitBreakerConfig.config
  private val defaultBreakerConfig = circuitBreakerConfig.default
  private val breakers = new ConcurrentHashMap[String, Option[CircuitBreakerHolder]]

  def withCircuitBreaker[T](breakerId: CircuitBreakerId)(body: ⇒ Future[T]): Future[T] = {
    val id = breakerId.id
    breaker(id) match {
      case Some(CircuitBreakerHolder(b, metrics)) =>
        val startTime = System.nanoTime()
        def elapsed: Long = System.nanoTime() - startTime
        val result = b.withCircuitBreaker(body)
        result.onComplete {
          case Success(_)                              => metrics.onCallSuccess(elapsed)
          case Failure(_: CircuitBreakerOpenException) => metrics.onCallBreakerOpenFailure()
          case Failure(_: TimeoutException)            => metrics.onCallTimeoutFailure(elapsed)
          case Failure(_)                              => metrics.onCallFailure(elapsed)
        }(system.dispatcher)
        result
      case None => body
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

class CircuitBreakerModule extends AbstractModule {

  @Override
  def configure(): Unit = {
    bind(classOf[CircuitBreaker])
  }

  @Provides
  @Inject
  def provideCircuitBreakerMetrics(system: ActorSystem, injector: Injector): CircuitBreakerMetricsProvider = {
    val implClass = system.settings.config.getString("lagom.spi.circuit-breaker-metrics-class") match {
      case "" =>
        classOf[CircuitBreakerMetricsProviderImpl]
      case className =>
        system.asInstanceOf[ExtendedActorSystem].dynamicAccess.getClassFor[CircuitBreakerMetricsProvider](className).get
    }
    injector.getInstance(implClass)
  }
}

@Singleton
class CircuitBreakerConfig @Inject() (system: ActorSystem) {
  val config: Config = system.settings.config.getConfig("lagom.circuit-breaker")
  val default: Config = config.getConfig("default")
}
