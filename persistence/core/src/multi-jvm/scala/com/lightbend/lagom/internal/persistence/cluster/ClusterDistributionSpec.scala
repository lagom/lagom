/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.BootstrapSetup
import akka.actor.Props
import akka.actor.setup.ActorSystemSetup
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.scaladsl.persistence.multinode.STMultiNodeSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object ClusterDistributionConfig extends MultiNodeConfig {
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
  def createActorSystem(): Config => ActorSystem = { config =>
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

  //  -------- A first piece of testing scaffolding and cluster formation  ------------------
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

  //  -------- Before this line all code is testing scaffolding or cluster formation  ------------------

  private val ensureActiveInterval: FiniteDuration = 1.second
  val distributionSettings =
    ClusterDistributionSettings(system)
      .copy(ensureActiveInterval = ensureActiveInterval)

  "A ClusterDistribution" must {

    "distribute the entityIds across nodes (so all nodes get a response)" in {

      val probe = TestProbe()

      val typeName = "CDTest"
      // There'll be 3 nodes in this test and each node will host a TestProbe.
      // Cluster Distribution on each node will create a `FakeActor.props` pointing
      // back to its own TestProbe. We request the creation of 10 FakeActor to ClusterDistribution
      // with the expectation that there'll be 3 or 4 FakeActor's for each TestProbe.
      val props: Props = FakeActor.props(probe.ref)
      val entityIds    = (1 to 10).map(i => s"test-entityId$i").toSet

      // Load the extension and wait for other nodes to be ready before proceeding
      val cdExtension  = ClusterDistribution(system)
      enterBarrier("extension-is-loaded")
      cdExtension.start(
        typeName,
        props,
        entityIds,
        distributionSettings
      )

      // During 10secs, or when 10 responses are obtained, record responses.
      // The reported messages are a Tuple2[sender.path, entityId] so the `sender.path` must be 3 different
      // values (representing each of the TestProbe's).
      val reportedMessages: Seq[(String, String)] = probe.receiveN(20, 10.second).asInstanceOf[Seq[(String, String)]]

      // Any of the 10 FakeActor instances will receive `EnsureActive` messages from any of the nodes. So, the
      // reported message must have 3 distinct senders.
      reportedMessages.map(_._1).distinct.size should be(3)

      // The `entityId`, OTOH, works differently. a TestProbe will only get reports from the FakeActor's
      // that were created locally so in the 10 reports we'll get always the same 3 or 4 entityId's.
      // The expected size is 3 <= x <= 4 because evenly distributing 10 shards in 3 nodes gives these numbers but
      // Travis causes the distribution to, sometimes, allocate only 2 shards in a node, so we relax both conditions.
      reportedMessages.map(_._2).distinct.size should be >= 2
      reportedMessages.map(_._2).distinct.size should be <= 5
    }
  }
}

import akka.actor.Actor
import akka.actor.Props

object FakeActor {
  def props(creatorRef: ActorRef): Props = Props(new FakeActor(creatorRef))
}

// This actor keeps a reference to the test instance that created it (this is a multi-node test
// so there are multiple tests instances).
// Each node in the cluster may send messages to this actor but this actor will only report back
// to its creator.
class FakeActor(creatorRef: ActorRef) extends Actor {
  override def receive = {
    case EnsureActive(entityId) => creatorRef ! (sender.path.address.toString, entityId)
  }
}
