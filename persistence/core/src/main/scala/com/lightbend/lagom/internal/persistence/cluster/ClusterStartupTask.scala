/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cluster

import akka.Done
import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, SupervisorStrategy }
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings }
import akka.pattern.BackoffSupervisor
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Performs an idempotent task on one node on cluster startup.
 *
 * The task guarantees that when the actor is asked to perform the operation, the operation will only be executed on
 * one node of the cluster at a time, and that when the returned future is redeemed, the task will be performed.
 *
 * This will start a cluster singleton which will execute the task. The task may be executed again when a new node
 * becomes the singleton, hence the task must be idempotent.
 *
 * If the task fails, it will be re-executed using exponential backoff using the given backoff parameters.
 */
object ClusterStartupTask {

  def apply(
    system:              ActorSystem,
    taskName:            String,
    task:                () => Future[Done],
    taskTimeout:         FiniteDuration,
    role:                Option[String],
    minBackoff:          FiniteDuration,
    maxBackoff:          FiniteDuration,
    randomBackoffFactor: Double
  ): ClusterStartupTask = {

    val startupTaskProps = Props(classOf[ClusterStartupTaskActor], task, taskTimeout)

    val backoffProps = BackoffSupervisor.propsWithSupervisorStrategy(
      startupTaskProps, taskName, minBackoff, maxBackoff, randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )

    val singletonProps = ClusterSingletonManager.props(backoffProps, PoisonPill,
      ClusterSingletonManagerSettings(system))

    val singleton = system.actorOf(singletonProps, s"$taskName-singleton")

    val singletonProxy = system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = singleton.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system).withRole(role)
      ), s"$taskName-singletonProxy"
    )

    new ClusterStartupTask(singletonProxy)
  }
}

class ClusterStartupTask(actorRef: ActorRef) {

  import ClusterStartupTaskActor._

  /**
   * Execute the task. The startup task will reply with [[akka.Done]] when it's done, or a
   * [[akka.actor.Status.Failure]] if the task failed or timed out.
   *
   * @param sender The sender to reply to.
   */
  def execute()(implicit sender: ActorRef): Unit = {
    actorRef ! Execute
  }

  /**
   * Request the task to be executed using the ask pattern.
   *
   * @return A future of the result.
   */
  def askExecute()(implicit timeout: Timeout): Future[Done] = {
    import akka.pattern.ask

    (actorRef ? Execute).mapTo[Done]
  }
}

private[lagom] object ClusterStartupTaskActor {
  case object Execute
}

private[lagom] class ClusterStartupTaskActor(task: () => Future[Done], timeout: FiniteDuration) extends Actor with ActorLogging {

  import ClusterStartupTaskActor._

  import akka.pattern.ask
  import akka.pattern.pipe

  import context.dispatcher

  override def preStart(): Unit = {
    // We let the ask pattern handle the timeout, by asking ourselves to execute the task and piping the result back to
    // ourselves
    implicit val askTimeout = Timeout(timeout)
    self ? Execute pipeTo self
  }

  def receive = {
    case Execute =>
      log.info(s"Executing cluster start task ${self.path.name}.")
      task() pipeTo self
      context become executing(List(sender()))
  }

  def executing(outstandingRequests: List[ActorRef]): Receive = {
    case Execute =>
      context become executing(sender() :: outstandingRequests)

    case Done =>
      log.info(s"Cluster start task ${self.path.name} done.")
      outstandingRequests foreach { requester =>
        requester ! Done
      }
      context become executed

    case failure @ Failure(e) =>
      outstandingRequests foreach { requester =>
        requester ! failure
      }
      // If we failed to prepare, crash
      throw e
  }

  def executed: Receive = {
    case Execute =>
      sender() ! Done

    case Done =>
    // We do expect to receive Done once executed since we initially asked ourselves to execute
  }

}
