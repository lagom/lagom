/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster

import akka.actor.ActorSystem
import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import com.lightbend.lagom.scaladsl.persistence.multinode.STMultiNodeSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object ClusterDistributionConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")
}

// heavily inspired by AbstractClusteredPersistentEntitySpec
object ClusterDistributionSpec {
  // Copied from MultiNodeSpec
  private def getCallerName(clazz: Class[_]): String = {
    val s = Thread.currentThread.getStackTrace.map(_.getClassName).drop(1).dropWhile(_.matches(".*MultiNodeSpec.?$"))
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s.drop(z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }
  def createActorSystem(): (Config) => ActorSystem = { config =>
    val setup = ActorSystemSetup(BootstrapSetup(ConfigFactory.load(config)))
    ActorSystem(getCallerName(classOf[MultiNodeSpec]), setup)
  }
}

class ClusterDistributionSpecMultiJvmNode1 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode2 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode3 extends ClusterDistributionSpec

class ClusterDistributionSpec
    extends MultiNodeSpec(ClusterDistributionConfig, ClusterDistributionSpec.createActorSystem())
    with STMultiNodeSpec
    with ImplicitSender {

  import ClusterDistributionConfig._
  override def initialParticipants: Int = roles.size

  "A ClusterDistribution" must {

    "distribute" in {}
  }
}
