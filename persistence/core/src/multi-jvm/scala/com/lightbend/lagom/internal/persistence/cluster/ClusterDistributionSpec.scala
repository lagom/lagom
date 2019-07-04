/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster

import akka.actor.ActorSystem
import akka.actor.BootstrapSetup
import akka.actor.Props
import akka.actor.setup.ActorSystemSetup
import akka.cluster.sharding.ClusterShardingSettings
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.scaladsl.persistence.multinode.STMultiNodeSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.Duration

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
    val testConfig = ConfigFactory.parseString("akka.actor.provider = cluster").withFallback(config)
    val setup      = ActorSystemSetup(BootstrapSetup(ConfigFactory.load(testConfig)))
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

  val distributionSettings =
    ClusterDistributionSettings(system)
      .copy(ensureActiveInterval = Duration(1, "second"))

  "A ClusterDistribution" must {

    "distribute" in {
      val typeName     = "CDTest"
      val props: Props = FakeActor.props
      val tagNames     = (1 to 10).map(i => s"test$i").toSet
      ClusterDistribution(system)
        .start(
          typeName,
          props,
          tagNames,
          distributionSettings
        )
    }
  }
}

import akka.actor.Actor
import akka.actor.Props

object FakeActor {
  def props: Props = Props(new FakeActor)
}

class FakeActor extends Actor {
  override def receive = {
    case EnsureActive(tagName) => println(tagName)
  }
}
