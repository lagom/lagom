/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.cluster

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.google.inject.AbstractModule
import com.google.inject.Inject
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.util.Try

class JoinClusterModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[JoinCluster]).asEagerSingleton()
  }
}

private[lagom] class JoinCluster @Inject() (system: ActorSystem) {

  private val config = system.settings.config
  private def joinSelf = config.getBoolean("lagom.cluster.join-self")
  private def terminateSystemAfter = config.getDuration(
    "lagom.cluster.terminate-system-after-member-removed", TimeUnit.MILLISECONDS
  ).millis
  private def exitJvm = config.getBoolean("lagom.cluster.exit-jvm-when-system-terminated")

  // join self if seed-nodes are not configured in dev-mode,
  // otherwise it will join the seed-nodes automatically
  private val cluster = Cluster(system)
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

