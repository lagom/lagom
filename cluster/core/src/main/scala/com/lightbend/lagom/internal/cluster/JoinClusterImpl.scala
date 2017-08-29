/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.cluster

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import akka.actor.ActorSystem
import akka.cluster.Cluster

private[lagom] object JoinClusterImpl {

  def join(system: ActorSystem): Unit = {

    val config = system.settings.config

    def joinSelf = config.getBoolean("lagom.cluster.join-self")

    def terminateSystemAfter = config.getDuration(
      "lagom.cluster.terminate-system-after-member-removed", TimeUnit.MILLISECONDS
    ).millis

    def exitJvm = config.getBoolean("lagom.cluster.exit-jvm-when-system-terminated")

    // join self if seed-nodes are not configured in dev-mode,
    // otherwise it will join the seed-nodes automatically
    val cluster = Cluster(system)
    if (cluster.settings.SeedNodes.isEmpty && joinSelf)
      cluster.join(cluster.selfAddress)

    val SUCCESSFUL_EXIT = 0
    val exitStatus = new AtomicInteger(SUCCESSFUL_EXIT)

    val isExiting = new AtomicBoolean(false)

    if (exitJvm) {
      // exit JVM when ActorSystem has been terminated
      system.registerOnTermination {
        new Thread("Sys-exiting-from-akka-termination") {
          override def run(): Unit = {
            if (isExiting.compareAndSet(false, true)) {
              println("Proceed to JVM shutdown with exit status: " + exitStatus.get())
              System.exit(exitStatus.get())
            } else {
              println("JVM shutdown already handled. Ignore.")
            }
          }
        }
      }
    }

    Cluster(system).registerOnMemberRemoved {
      // The delay is to give ClusterSingleton actors some time to stop gracefully.
      system.scheduler.scheduleOnce(terminateSystemAfter) {
        val CLUSTER_MEMBERSHIP_REMOVED = -128
        val EXIT_TIMEOUT_WITH_HALT = -2
        exitStatus.compareAndSet(SUCCESSFUL_EXIT, CLUSTER_MEMBERSHIP_REMOVED)

        // `needsExiting` must be set before invoking `system.terminate`
        val needsExiting = isExiting.compareAndSet(false, true)
        system.terminate()

        if (exitJvm) {
          // In case ActorSystem shutdown takes longer than 10 seconds,
          // exit the JVM forcefully anyway.
          // We must spawn a separate thread to not block current thread,
          // since that would have blocked the shutdown of the ActorSystem.
          val t = new Thread(new Runnable {
            override def run(): Unit = {
              if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure) {
                System.err.println("Halting JVM.")
                Runtime.getRuntime.halt(EXIT_TIMEOUT_WITH_HALT)
              }
              // this is reached only when `system.whenTerminated` completes successfully.
              if (needsExiting) {
                println("Proceed to JVM shutdown with exit status: " + exitStatus.get())
                System.exit(exitStatus.get())
              }
            }
          })
          t.setDaemon(true)
          t.start()
        }
      }(system.dispatcher)

    }
  }

}
