/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

import akka.Done
import akka.actor.{ ActorSystem, CoordinatedShutdown, ExtendedActorSystem }
import akka.actor.CoordinatedShutdown._
import akka.cluster.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.lightbend.lagom.internal.akka.management.AkkaManagementTrigger
import play.api.{ Environment, Mode }

import scala.concurrent.Future

private[lagom] object JoinClusterImpl {

  def join(system: ActorSystem, environment: Environment, akkaManagementTrigger: AkkaManagementTrigger): Unit = {
    val config = system.settings.config
    val joinSelf = config.getBoolean("lagom.cluster.join-self")

    val clusterBootstrapEnabled = config.getBoolean("lagom.cluster.bootstrap.enabled")
    val exitJvm = config.getBoolean("lagom.cluster.exit-jvm-when-system-terminated")
    val isProd: Boolean = environment.mode == Mode.Prod

    // join self if seed-nodes are not configured in dev-mode,
    // otherwise it will join the seed-nodes automatically
    val cluster = Cluster(system)

    if (isProd && joinSelf) {
      system.log.warning("The \"lagom.cluster.join-self\" setting should not be enabled in production, because it can " +
        "conflict with Akka Cluster Bootstrap or cause split-brain clusters.")
    }

    if (clusterBootstrapEnabled && joinSelf) {
      throw new IllegalArgumentException(
        "Both \"lagom.cluster.bootstrap.enabled\" and \"lagom.cluster.join-self\" are enabled, you should enable only one. " +
          "Typically, \"lagom.cluster.bootstrap.enabled\" should be used in production while \"lagom.cluster.join-self\" in development and test environments"
      )
    }

    /*
     * There are several ways to form a cluster in Lagom
     *  1. Declared seed-nodes. Highest priority setting.
     *  2. lagom.cluster.bootstrap.enabled - Lagom's prod default. Uses Akka Cluster Bootstrap and Akka Management
     *  3. lagom.cluster.join-self - Lagom's dev mode forms a single node cluster (unrecommended for production)
     *  4. Programmatically: No seed-nodes, join-self = false and bootstrap.enabled = false. User is on its own to form the cluster
     *
     *  Last option is rather unusual but allows users to fallback to plain Akka cluster formation API. This option
     *  is used in Lagom tests, for example.
     *  In order to join programmatically, one need to disable all flags.
     *
     *  The code below will form the cluster if there are no seed-nodes and bootstrap or join-self are enabled. Forming
     *  the cluster when seed-nodes are defined is already handled by Akka internally so there's no need to add
     *  extra code in Lagom's codebase.
     */
    if (cluster.settings.SeedNodes.isEmpty) {

      if (clusterBootstrapEnabled) {

        // akka-management is a hard requirement for Akka ClusterBootstrap
        // therefore, we must make sure it get started.
        // forcedStart won't honour `lagom.akka.management.enabled` and will start akka-management anyway
        // this call has no effect if akka-management is already running
        akkaManagementTrigger.forcedStart("Akka Cluster Bootstrap")

        // we should only run ClusterBootstrap if the user didn't configure the seed-needs
        // and left clusterBootstrapEnabled on true (default)
        ClusterBootstrap(system.asInstanceOf[ExtendedActorSystem]).start()

      } else if (joinSelf) {
        cluster.join(cluster.selfAddress)
      }

    }

    CoordinatedShutdown(system).addTask(PhaseClusterShutdown, "exit-jvm-when-downed") {
      () =>
        val shutdownReason: Option[Reason] = CoordinatedShutdown(system).shutdownReason()

        val reasons: Seq[Reason] = Seq(
          ClusterDowningReason,
          ClusterJoinUnsuccessfulReason,
          IncompatibleConfigurationDetectedReason
        )

        val reasonIsDowning: Boolean = shutdownReason.exists(reasons.contains)

        // if 'exitJvm' is enabled and the trigger (aka Reason) for CoordinatedShutdown is ClusterDowning,
        // JoinUnsuccessful, etc... we must exit the JVM. This can lead to the cluster closing before
        // the `Application` but we're out of the cluster (downed) already so the impact is acceptable.
        if (exitJvm && reasonIsDowning) {

          // If this code is running, it means CoordinatedShutdown was triggered. CoordinatedShutdown of
          // a given actor system can only be invoked once: further invocations block until the initial
          // one completes. The code below works as following:
          //   - create a new Thread and invoke System.exit(-1)
          //   - terminate the "exit-jvm-when-downed" task
          //   - in parallel, the invocation of System.exit(-1) has triggered the execution of the JVM shutdown Hooks
          //   - Play's JVM shutdown hooks proceed to stop Play which means stopping the Server and the
          //     Application. That also involves Play's ApplicationLifecycle. In a particular step of that stop
          //     process Play must stop its Actor System so it invokes CoordinatedShutdown. Since the
          //     CoordinatedShutdown is already running, that operation blocks.
          // !! At this point, there's a CoordinatedShutdown running (triggered by a Downing event) and a
          //    reference to a CoordinatedShutdown blocked until the run completes. The thread blocked is the
          //    JVM shutdown thread.
          //  - When the main CoordinatedShutdown completes, the JVM keeps running because Play (and Lagom)
          //    tune Akka's `exit-jvm=off`.
          //  - When the main CoordinatedShutdown completes, the thread blocked on the main CoordinatedShutdown
          //    unblocks and completes the execution of `System.exit`.
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
