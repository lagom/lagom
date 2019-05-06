/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.cluster

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.cluster.Cluster
import play.api.Environment
import play.api.Mode

import scala.concurrent.Future
import scala.concurrent.duration._

private[lagom] object JoinClusterImpl {

  def join(system: ActorSystem, environment: Environment): Unit = {
    val config          = system.settings.config
    val joinSelf        = config.getBoolean("lagom.cluster.join-self")
    val exitJvm         = config.getBoolean("lagom.cluster.exit-jvm-when-system-terminated")
    val isProd: Boolean = environment.mode == Mode.Prod

    // join self if seed-nodes are not configured in dev-mode,
    // otherwise it will join the seed-nodes automatically
    val cluster = Cluster(system)

    if (isProd && joinSelf) {
      system.log.warning(
        "The \"lagom.cluster.join-self\" setting should not be enabled in production, because it can " +
          "conflict with Akka Cluster Bootstrap or cause split-brain clusters."
      )
    }

    if (cluster.settings.SeedNodes.isEmpty && joinSelf)
      cluster.join(cluster.selfAddress)

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseClusterShutdown, "exit-jvm-when-downed") { () =>
      val reasonIsDowning = CoordinatedShutdown(system)
        .shutdownReason()
        .contains(CoordinatedShutdown.ClusterDowningReason)

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
            // exit code when shutting down because of a cluster Downing event must be non-zero
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
