/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.Scheduler
import com.lightbend.lagom.persistence.ActorSystemSpec
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }

class RetrySpec extends ActorSystemSpec with ScalaFutures {
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  lazy val exception = new RuntimeException("fail")

  "Retry" must {
    "succeed when the operation succeeds immediately" in {
      val future = Retry(2.seconds, 1.0, 0)(Done)
      future.futureValue shouldBe Done
    }

    "fail when the operation fails repeatedly" in {
      val future = Retry(10.milliseconds, 1.0, 3)(throw exception)
      future.failed.futureValue shouldBe exception
    }

    "retry up to the maximum number of attempts" in {
      val attemptCount = new AtomicInteger(0)
      val future = Retry(10.milliseconds, 1.0, 2) {
        attemptCount.getAndIncrement()
        throw exception
      }
      whenReady(future.failed) { result =>
        result shouldBe exception
        attemptCount.get shouldBe 3 // initial attempt + two retries
      }
    }

    "retry only until successful" in {
      val attemptCount = new AtomicInteger(0)
      val future = Retry(10.milliseconds, 1.0, 2) {
        val attempts = attemptCount.getAndIncrement()
        if (attempts > 0) Done
        else throw new RuntimeException("fail")
      }
      whenReady(future) { result =>
        result shouldBe Done
        attemptCount.get shouldBe 2 // initial attempt + first retry
      }
    }

    "invoke the onRetry handler with the next delay and number of remaining attempts" in {
      val retryCount = new AtomicInteger(0)
      val maxRetries = 3
      val retry = new Retry(10.milliseconds, 2.0, maxRetries) {
        override protected def onRetry(throwable: Throwable, delay: FiniteDuration, remainingRetries: Int): Unit = {
          val retries = retryCount.getAndIncrement()
          retries match {
            case 0 =>
              remainingRetries shouldBe 3
              delay shouldBe 10.milliseconds
            case 1 =>
              remainingRetries shouldBe 2
              delay shouldBe 20.milliseconds
            case 2 =>
              remainingRetries shouldBe 1
              delay shouldBe 40.milliseconds
          }
        }
      }

      val future = retry(throw exception)
      whenReady(future.failed) { result =>
        result shouldBe exception
        retryCount.get shouldBe maxRetries
      }
    }

    "delay each retry" in {
      val attemptCount = new AtomicInteger(0)
      val maxRetries = 3
      @volatile var lastAttemptTimestamp: Long = System.nanoTime
      val future = Retry(100.milliseconds, 2.0, maxRetries) {
        val attempts = attemptCount.getAndIncrement()
        val elapsedSinceLastAttempt = Duration.fromNanos(System.nanoTime - lastAttemptTimestamp)
        attempts match {
          case 0 => elapsedSinceLastAttempt should be < 100.milliseconds // more or less immediately
          case 1 => elapsedSinceLastAttempt should be > 100.milliseconds // after the initial delay
          case 2 => elapsedSinceLastAttempt should be > 200.milliseconds // applying the back-off factor
          case 3 => elapsedSinceLastAttempt should be > 400.milliseconds // applying the back-off factor again
        }
        lastAttemptTimestamp = System.nanoTime
        throw exception
      }

      whenReady(future.failed, timeout(1.second)) { result =>
        result shouldBe exception
        attemptCount.get shouldBe maxRetries + 1
      }
    }
  }
}
