/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ CopyOnWriteArrayList, TimeUnit }
import javax.inject.{ Inject, Provider, Singleton }

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import com.codahale.metrics._
import com.lightbend.lagom.internal.spi.{ CircuitBreakerMetrics, CircuitBreakerMetricsProvider }
import play.api.inject.{ Binding, Injector, Module }
import play.api.{ Configuration, Environment, Logger }

class CircuitBreakerModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[CircuitBreakers].toSelf,
      bind[CircuitBreakerMetricsProvider].toProvider[CircuitBreakerMetricsProviderProvider]
    )
  }
}

@Singleton
class CircuitBreakerMetricsProviderProvider @Inject() (system: ActorSystem, injector: Injector) extends Provider[CircuitBreakerMetricsProvider] {
  lazy val get = {
    val implClass = system.settings.config.getString("lagom.spi.circuit-breaker-metrics-class") match {
      case ""        => classOf[CircuitBreakerMetricsProviderImpl]
      case className => system.asInstanceOf[ExtendedActorSystem].dynamicAccess.getClassFor[CircuitBreakerMetricsProvider](className).get
    }

    injector.instanceOf(implClass)
  }
}

@Singleton
class CircuitBreakerMetricsProviderImpl @Inject() (val system: ActorSystem) extends CircuitBreakerMetricsProvider {
  private[lagom] val registry = new MetricRegistry
  private val metrics = new CopyOnWriteArrayList[CircuitBreakerMetricsImpl]

  override def start(breakerId: String): CircuitBreakerMetrics = {
    val m = new CircuitBreakerMetricsImpl(breakerId, this)
    metrics.add(m)
    m
  }

  private[lagom] def remove(m: CircuitBreakerMetricsImpl): Unit =
    metrics.remove(m)

  private[lagom] def allMetrics(): java.util.List[CircuitBreakerMetricsImpl] =
    metrics
}

object CircuitBreakerMetricsImpl {
  final val Closed = "closed"
  final val Open = "open"
  final val HalfOpen = "half-open"

  private final def stateName(breakerId: String) = MetricRegistry.name("CircuitBreaker", "state", breakerId)
  private final def successCountName(breakerId: String) = MetricRegistry.name("CircuitBreaker", "successCount", breakerId)
  private final def failureCountName(breakerId: String) = MetricRegistry.name("CircuitBreaker", "failureCount", breakerId)
  private final def latencyName(breakerId: String) = MetricRegistry.name("CircuitBreaker", "latency", breakerId)
  private final def throughputName(breakerId: String) = MetricRegistry.name("CircuitBreaker", "throughput", breakerId)
  private final def failureThroughputName(breakerId: String) =
    MetricRegistry.name("CircuitBreaker", "failureThroughput", breakerId)
}

class CircuitBreakerMetricsImpl(val breakerId: String, provider: CircuitBreakerMetricsProviderImpl)
  extends CircuitBreakerMetrics {
  import CircuitBreakerMetricsImpl._

  private val log = Logger(getClass)
  private val stateValue = new AtomicReference[String](Closed)

  private def registry = provider.registry

  val successCount: Counter = registry.counter(successCountName(breakerId))
  val failureCount: Counter = registry.counter(failureCountName(breakerId))
  val latency: Histogram = registry.histogram(latencyName(breakerId)) //using ExponentiallyDecayingReservoir
  val throughput: Meter = registry.meter(throughputName(breakerId))
  val failureThroughput: Meter = registry.meter(failureThroughputName(breakerId))
  val state: Gauge[String] = registry.register(stateName(breakerId), new Gauge[String] {
    override def getValue: String = stateValue.get
  })

  override def onOpen(): Unit = {
    stateValue.compareAndSet(Closed, Open)
    stateValue.compareAndSet(HalfOpen, Open)
    log.warn(s"Circuit breaker [${breakerId}] open")
  }

  override def onClose(): Unit = {
    stateValue.compareAndSet(Open, Closed)
    stateValue.compareAndSet(HalfOpen, Closed)
    log.info(s"Circuit breaker [${breakerId}] closed")
  }

  override def onHalfOpen(): Unit = {
    stateValue.compareAndSet(Open, HalfOpen)
    log.info(s"Circuit breaker [${breakerId}] half-open")
  }

  override def onCallSuccess(elapsedNanos: Long): Unit = {
    updateThroughput()
    updateLatency(elapsedNanos)
    updateSuccessCount()
  }

  override def onCallFailure(elapsedNanos: Long): Unit = {
    updateThroughput()
    updateFailureThroughput()
    updateLatency(elapsedNanos)
    updateFailureCount()
  }

  override def onCallTimeoutFailure(elapsedNanos: Long): Unit = {
    updateThroughput()
    updateFailureThroughput()
    updateLatency(elapsedNanos)
    updateFailureCount()
  }

  override def onCallBreakerOpenFailure(): Unit = {
    updateThroughput()
    updateFailureThroughput()
    updateFailureCount()
  }

  override def stop(): Unit = {
    registry.remove(successCountName(breakerId))
    registry.remove(failureCountName(breakerId))
    registry.remove(latencyName(breakerId))
    registry.remove(throughputName(breakerId))
    registry.remove(failureThroughputName(breakerId))
    registry.remove(stateName(breakerId))
    provider.remove(this)
  }

  private def updateSuccessCount(): Unit =
    successCount.inc()

  private def updateFailureCount(): Unit =
    failureCount.inc()

  private def updateLatency(elapsedNanos: Long): Unit =
    latency.update(TimeUnit.NANOSECONDS.toMicros(elapsedNanos))

  private def updateThroughput(): Unit =
    throughput.mark()

  private def updateFailureThroughput(): Unit =
    failureThroughput.mark()

}
