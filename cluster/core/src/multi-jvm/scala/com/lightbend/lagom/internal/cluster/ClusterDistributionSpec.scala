/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

import akka.actor.ActorRef
import akka.actor.Props
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive

import scala.concurrent.duration._


class ClusterDistributionSpecMultiJvmNode1 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode2 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode3 extends ClusterDistributionSpec

class ClusterDistributionSpec extends ClusteredMultiNodeUtils {


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

      // During 10secs, or when 50 responses are obtained, record responses.
      // The reported messages are a Tuple2[sender.path, entityId] so the `sender.path` must be 3 different
      // values (representing each of the TestProbe's).
      val reportedMessages: Seq[(String, String)] = probe.receiveN(50, 10.second).asInstanceOf[Seq[(String, String)]]

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
