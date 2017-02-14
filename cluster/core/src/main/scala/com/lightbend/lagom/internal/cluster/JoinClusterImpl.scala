/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.cluster

import java.util.concurrent.TimeUnit

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

    if (exitJvm) {
      // exit JVM when ActorSystem has been terminated
      system.registerOnTermination(System.exit(0))
    }

    Cluster(system).registerOnMemberRemoved {
      // The delay is to give ClusterSingleton actors some time to stop gracefully.
      system.scheduler.scheduleOnce(terminateSystemAfter) {
        system.terminate()

        if (exitJvm) {
          // In case ActorSystem shutdown takes longer than 10 seconds,
          // exit the JVM forcefully anyway.
          // We must spawn a separate thread to not block current thread,
          // since that would have blocked the shutdown of the ActorSystem.
          val t = new Thread(new Runnable {
            override def run(): Unit = {
              if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure)
                System.exit(-1)
            }
          })
          t.setDaemon(true)
          t.start()
        }
      }(system.dispatcher)

    }
  }

}
