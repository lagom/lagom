/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server.status

import java.time.Instant

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.client.{ CircuitBreakerMetricsImpl, CircuitBreakerMetricsProviderImpl }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.scaladsl.api.{ Service, ServiceCall }
import com.lightbend.lagom.scaladsl.server.{ LagomServerComponents, LagomServiceBinder, LagomServiceBinding }
import play.api.libs.json.{ Format, Json }

import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

trait MetricsService extends Service {
  /**
   * Snapshot of current circuit breaker status
   */
  def currentCircuitBreakers: ServiceCall[NotUsed, immutable.Seq[CircuitBreakerStatus]]

  /**
   * Stream of circuit breaker status
   */
  def circuitBreakers: ServiceCall[NotUsed, Source[immutable.Seq[CircuitBreakerStatus], Any]]

  override def descriptor = {
    import Service._

    named("metrics").withCalls(
      pathCall("/_status/circuit-breaker/current", currentCircuitBreakers),
      pathCall("/_status/circuit-breaker/stream", circuitBreakers)
    ).withLocatableService(false)
  }
}

/**
 * Provides an in-built metrics service.
 */
trait MetricsServiceComponents extends LagomServerComponents {
  def circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider

  lazy val metricsServiceBinding: LagomServiceBinding[MetricsService] = {
    // Can't use the bindService macro here, since it's in the same compilation unit. The code below is exactly what
    // the macro generates.
    LagomServiceBinder(lagomServerBuilder, new MetricsService {
      override def circuitBreakers: ServiceCall[NotUsed, Source[Seq[CircuitBreakerStatus], Any]] =
        throw new NotImplementedError("Service methods and topics must not be invoked from service trait")
      override def currentCircuitBreakers: ServiceCall[NotUsed, Seq[CircuitBreakerStatus]] =
        throw new NotImplementedError("Service methods and topics must not be invoked from service trait")
    }.descriptor).to(new MetricsServiceImpl(circuitBreakerMetricsProvider)(executionContext))
  }
}

case class CircuitBreakerStatus(
  id:                        String,
  timestamp:                 Instant,
  state:                     String,
  totalSuccessCount:         Long,
  totalFailureCount:         Long,
  throughputOneMinute:       Double,
  failedThroughputOneMinute: Double,
  latencyMicros:             Latency
)

object CircuitBreakerStatus {
  implicit val format: Format[CircuitBreakerStatus] = Json.format
}

case class Latency(
  median:          Double,
  percentile98th:  Double,
  percentile99th:  Double,
  percentile999th: Double,
  mean:            Double,
  min:             Long,
  max:             Long
)

object Latency {
  implicit val format: Format[Latency] = Json.format
}

private class MetricsServiceImpl(circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider)(implicit ec: ExecutionContext) extends MetricsService {

  override def currentCircuitBreakers = ServiceCall { _ =>
    Future.successful(allCircuitBreakerStatus)
  }

  override def circuitBreakers = ServiceCall { _ =>
    val source = Source.tick(100.milliseconds, 2.seconds, "tick").map { _ =>
      allCircuitBreakerStatus
    }
    Future.successful(source)
  }

  private def allCircuitBreakerStatus: immutable.Seq[CircuitBreakerStatus] = {
    import scala.collection.JavaConverters._
    circuitBreakerMetricsProvider.asInstanceOf[CircuitBreakerMetricsProviderImpl].allMetrics().asScala.view
      .flatMap { m =>
        try {
          Seq(circuitBreakerStatus(m))
        } catch {
          case NonFatal(e) =>
            // might happen if the circuit breaker is removed, just ignore
            Nil
        }
      }.to[immutable.Seq]
  }

  private def circuitBreakerStatus(m: CircuitBreakerMetricsImpl): CircuitBreakerStatus = {
    val latencyHistogram = m.latency.getSnapshot
    val latency = Latency(
      median = latencyHistogram.getMedian,
      percentile98th = latencyHistogram.get98thPercentile,
      percentile99th = latencyHistogram.get99thPercentile,
      percentile999th = latencyHistogram.get999thPercentile,
      min = latencyHistogram.getMin,
      max = latencyHistogram.getMax,
      mean = latencyHistogram.getMean
    )
    CircuitBreakerStatus(
      id = m.breakerId,
      timestamp = Instant.now(),
      state = m.state.getValue,
      totalSuccessCount = m.successCount.getCount,
      totalFailureCount = m.failureCount.getCount,
      throughputOneMinute = m.throughput.getOneMinuteRate,
      failedThroughputOneMinute = m.failureThroughput.getOneMinuteRate,
      latencyMicros = latency
    )
  }
}
