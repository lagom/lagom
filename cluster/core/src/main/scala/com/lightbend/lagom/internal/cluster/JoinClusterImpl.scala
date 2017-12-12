/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.cluster

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.cluster.Cluster

import scala.concurrent.Future
import scala.concurrent.duration._

private[lagom] object JoinClusterImpl {

  def join(system: ActorSystem): Unit = {

    val config = system.settings.config
    def joinSelf = config.getBoolean("lagom.cluster.join-self")
    def exitJvm = config.getBoolean("lagom.cluster.exit-jvm-when-system-terminated")

    // join self if seed-nodes are not configured in dev-mode,
    // otherwise it will join the seed-nodes automatically
    val cluster = Cluster(system)
    if (cluster.settings.SeedNodes.isEmpty && joinSelf)
      cluster.join(cluster.selfAddress)

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseClusterShutdown, "exit-jvm-when-downed") {
      () =>
        val reasonIsDowning = CoordinatedShutdown(system)
          .shutdownReason().contains(CoordinatedShutdown.ClusterDowningReason)

        // if 'exitJvm' is enabled and the trigger (aka Reason) for CoordinatedShutdown is ClusterDowning
        // we must exit the JVM. This can lead to the cluster closing before the `Application` but we're
        // out of the cluster (downed) already so the impact is acceptable. This will be improved when
        // Play delegates all shutdown logic to CoordinatedShutdown in Play 2.7.x.
        if (exitJvm && reasonIsDowning) {
          // In case ActorSystem shutdown takes longer than 10 seconds,
          // exit the JVM forcefully anyway.
          // We must spawn a separate thread to not block current thread,
          // since that would have blocked the shutdown of the ActorSystem.
          val t = new Thread(new Runnable {
            override def run(): Unit = {
              System.exit(-1)
            }
          })
          t.setDaemon(true)
          t.start()
        }
        Future.successful(Done)
    }
  }

}
