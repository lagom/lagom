/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.remote.testconductor.RoleName
import com.typesafe.config.ConfigFactory
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import com.lightbend.lagom.internal.persistence.cluster.ClusterMultiNodeConfig.node1
import com.lightbend.lagom.scaladsl.persistence.multinode.STMultiNodeSpec
import com.typesafe.config.Config

import scala.concurrent.duration._

/**
 *
 */
// this is reused in multiple multi-jvm tests. There's still some copy/paste around though.
object ClusterMultiNodeConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  commonConfig(
    ConfigFactory
      .parseString(
        """
      akka.actor.provider = cluster
      terminate-system-after-member-removed = 60s

      # increase default timeouts to leave wider margin for Travis.
      # 30s to 60s
      akka.testconductor.barrier-timeout=60s
      akka.test.single-expect-default = 15s

      akka.cluster.sharding.waiting-for-state-timeout = 5s

      # Don't terminate the actor system when doing a coordinated shutdown
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off

      ## The settings below are incidental because this code lives in a project that depends on lagom-cluster and
      ## lagom-akka-management-core.

      # multi-jvm tests forms the cluster programmatically
      # therefore we disable Akka Cluster Bootstrap
      lagom.cluster.bootstrap.enabled = off

      # no jvm exit on tests
      lagom.cluster.exit-jvm-when-system-terminated = off
    """
      )
  )

}

// heavily inspired by AbstractClusteredPersistentEntitySpec
// this is reused in multiple multi-jvm tests. There's still some copy/paste around though.
object ClusterMultiNodeActorSystemFactory {
  // Copied from MultiNodeSpec
  private def getCallerName(clazz: Class[_]): String = {
    val s = Thread.currentThread.getStackTrace.map(_.getClassName).drop(1).dropWhile(_.matches(".*MultiNodeSpec.?$"))
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s.drop(z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }
  def createActorSystem(): Config => ActorSystem = { config =>
    val setup = ActorSystemSetup(BootstrapSetup(ConfigFactory.load(config)))
    ActorSystem(getCallerName(classOf[MultiNodeSpec]), setup)
  }
}

abstract class ClusteredMultiNodeUtils
    extends MultiNodeSpec(ClusterMultiNodeConfig, ClusterMultiNodeActorSystemFactory.createActorSystem())
    with STMultiNodeSpec
    with ImplicitSender {
  override def initialParticipants: Int = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system).join(node(to).address)
    }
    enterBarrier(from.name + "-joined")
  }

  def fullAddress(ref: ActorRef): Address =
    if (ref.path.address.hasLocalScope) Cluster(system).selfAddress
    else ref.path.address

  protected override def atStartup() {
    roles.foreach(n => join(n, node1))
    within(15.seconds) {
      awaitAssert(Cluster(system).state.members.size should be(3))
      awaitAssert(
        Cluster(system).state.members.toIndexedSeq.map(_.status).distinct should be(IndexedSeq(MemberStatus.Up))
      )
    }

    enterBarrier("startup")
  }

}
