/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

import akka.Done
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Scheduler
import akka.annotation.ApiMayChange
import akka.cluster.ddata.DistributedData
import akka.cluster.ddata.Flag
import akka.cluster.ddata.FlagKey
import akka.cluster.ddata.Replicator.Get
import akka.cluster.ddata.Replicator.GetSuccess
import akka.cluster.ddata.Replicator.ReadLocal
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata.Replicator.UpdateSuccess
import akka.cluster.ddata.Replicator.WriteAll
import akka.cluster.ddata.Replicator.WriteConsistency
import akka.cluster.ddata.SelfUniqueAddress
import akka.event.LoggingAdapter
import akka.testkit.TestProbe

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import akka.pattern.after
import akka.pattern.ask
import akka.util.Timeout

import scala.util.control.NonFatal

@ApiMayChange
class MultiNodeExpect(probe: TestProbe)(implicit system: ActorSystem) {
  private implicit val scheduler: Scheduler               = system.scheduler
  private implicit val executionContext: ExecutionContext = system.dispatcher

  val replicator: ActorRef             = DistributedData(system).replicator
  implicit val node: SelfUniqueAddress = DistributedData(system).selfUniqueAddress

  /**
   * Expect a message of type `T` in either the local `TestProbe` or any of the test probes
   * replicating under the provided `expectationKey` (which must not be reused).
   */
  def expectMsg[T](t: T, expectationKey: String, max: FiniteDuration): Future[Done] = {
    val eventualT = () => Future(errorAsRuntime(probe.expectMsg(max, t)))
    doExpect(eventualT)(expectationKey, max)
  }

  /**
   * Expect a message of type `T` in either the local `TestProbe` or any of the test probes
   * replicating under the provided `expectationKey` (which must not be reused).
   */
  def expectMsgType[T](expectationKey: String, max: FiniteDuration)(implicit t: ClassTag[T]): Future[Done] = {
    val eventualT = () => Future(errorAsRuntime(probe.expectMsgType[T](max)))
    doExpect(eventualT)(expectationKey, max)
  }

  // prevents Errors from turning into BoxedError when using `Future(f)` (where f throws Error)
  private def errorAsRuntime[T](f: => T): T = {
    try {
      f
    } catch {
      case NonFatal(t)  => throw t
      case x: Throwable => throw new RuntimeException(x)
    }
  }

  private def doExpect[T](eventualT: () => Future[T])(expectationKey: String, max: FiniteDuration): Future[Done] = {
    val DataKey: FlagKey           = FlagKey(expectationKey)
    val writeAll: WriteConsistency = WriteAll(max)
    implicit val timeout: Timeout  = Timeout(max)

    val retryDelay = 3.second

    val fTimeout = after(max, scheduler)(Future.failed(new RuntimeException(s"timeout $max expired")))

    // If the local expectation wins, it must notify others.
    val fLocalExpect: Future[Done] = eventualT()
      .map { _ =>
        (replicator ? Update(DataKey, Flag.empty, writeAll)(
          _.switchOn
        )).mapTo[UpdateSuccess[Flag]]
      }
      .map(_ => Done)

    // if a remote expectation wins, we can move on.
    val poll: () => Future[Done] = () =>
      (replicator ? Get(DataKey, ReadLocal)).map {
        case g @ GetSuccess(DataKey, _) if g.get(DataKey).enabled => Done
        case _                                                    => throw new RuntimeException("Flag unset")
      }
    val fRemoteExpect: Future[Done] = retry(
      poll,
      retryDelay,
      Int.MaxValue // keep retrying, there's a timeout later
    )

    Future
      .firstCompletedOf(
        Seq(
          fLocalExpect,
          fRemoteExpect,
          fTimeout
        )
      )
  }

  // From vklang's https://gist.github.com/viktorklang/9414163
  def retry[T](op: () => Future[T], delay: FiniteDuration, retries: Int): Future[T] =
    op().recoverWith { case _ if retries > 0 => after(delay, scheduler)(retry(op, delay, retries - 1)) }
}
