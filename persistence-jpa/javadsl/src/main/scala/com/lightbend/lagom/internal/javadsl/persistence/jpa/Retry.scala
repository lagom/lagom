/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import java.util.concurrent.CompletionStage
import java.util.function.Supplier

import akka.actor.Scheduler
import akka.pattern.after

import scala.concurrent.duration.Duration.fromNanos
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

// With thanks to https://gist.github.com/viktorklang/9414163
private[lagom] class Retry(delay: FiniteDuration, delayFactor: Double, maxRetries: Int) {
  def apply[T](op: => T)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    def iterate(nextDelay: FiniteDuration, remainingRetries: Int): Future[T] =
      Future(op) recoverWith {
        case NonFatal(throwable) if remainingRetries > 0 => {
          onRetry(throwable, nextDelay, remainingRetries)
          after(nextDelay, s)(iterate(finiteMultiply(nextDelay, delayFactor), remainingRetries - 1))
        }
      }

    iterate(delay, maxRetries)
  }

  // For convenient use from Java 8
  def retry[T](op: Supplier[T])(implicit ec: ExecutionContext, s: Scheduler): CompletionStage[T] = {
    import scala.compat.java8.FutureConverters._

    apply(op.get()).toJava
  }

  protected def onRetry(throwable: Throwable, delay: FiniteDuration, remainingRetries: Int): Unit = ()

  private def finiteMultiply(duration: FiniteDuration, factor: Double): FiniteDuration =
    fromNanos((duration.toNanos * factor).toLong)
}

private[lagom] object Retry {
  def apply[T](delay: FiniteDuration, delayFactor: Double, maxRetries: Int)(op: => T)(implicit ec: ExecutionContext, s: Scheduler): Future[T] =
    (new Retry(delay, delayFactor, maxRetries))(op)
}
