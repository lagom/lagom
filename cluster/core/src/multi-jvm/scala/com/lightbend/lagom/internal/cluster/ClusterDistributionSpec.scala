/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

import akka.actor.Props
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import akka.pattern._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._

import akka.cluster.sharding.ShardRegion.CurrentShardRegionState
import akka.cluster.sharding.ShardRegion.GetShardRegionState

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class ClusterDistributionSpecMultiJvmNode1 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode2 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode3 extends ClusterDistributionSpec

object ClusterDistributionSpec extends ClusterMultiNodeConfig {
  protected override def systemConfig: Config =
    ConfigFactory.parseString("""
      akka.cluster.sharding.rebalance-interval = 1s
      """).withFallback(super.systemConfig)
}

class ClusterDistributionSpec
    extends ClusteredMultiNodeUtils(numOfNodes = 3, ClusterDistributionSpec)
    with ScalaFutures
    with Eventually {
  private val ensureActiveInterval: FiniteDuration = 1.second
  private val distributionSettings: ClusterDistributionSettings =
    ClusterDistributionSettings(system)
      .copy(ensureActiveInterval = ensureActiveInterval)

  "A ClusterDistribution" must {
    "distribute the entityIds across nodes (so all nodes get a response)" in {
      val numOfEntities        = 20
      val minimalShardsPerNode = numOfEntities / numOfNodes

      val typeName = "CDTest"
      // There'll be 3 nodes in this test and each node will host a TestProbe.
      // Cluster Distribution on each node will create a `FakeActor.props` pointing
      // back to its own TestProbe. We request the creation of 10 FakeActor to ClusterDistribution
      // with the expectation that there'll be 3 or 4 FakeActor's for each TestProbe.
      val props: Props = FakeActor.props
      val entityIds    = (1 to numOfEntities).map(i => s"test-entity-id-$i").toSet

      // Load the extension and wait for other nodes to be ready before proceeding
      val cdExtension = ClusterDistribution(system)
      enterBarrier("cluster-distribution-extension-is-loaded")

      val shardRegion =
        cdExtension.start(
          typeName,
          props,
          entityIds,
          distributionSettings
        )

      // don't complete the test in a node, until other nodes had the change to complete their assertions too.
      completionBarrier("assertion-completed") {
        implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 45.seconds, interval = 200.millis)
        eventually {
          val shardRegionState =
            shardRegion
              .ask(GetShardRegionState)(3.seconds)
              .mapTo[CurrentShardRegionState]
              .futureValue

          shardRegionState.shards.size should be >= minimalShardsPerNode
        }
      }
    }
  }

  private def completionBarrier[T](barrierName: String)(block: => T): T = {
    try {
      block
    } finally {
      enterBarrier(barrierName)
    }
  }
}

import akka.actor.Actor
import akka.actor.Props

object FakeActor {
  def props: Props = Props(new FakeActor)
}

// This actor keeps a reference to the test instance that created it (this is a multi-node test
// so there are multiple tests instances).
// Each node in the cluster may send messages to this actor but this actor will only report back
// to its creator.
class FakeActor extends Actor {
  override def receive = {
    case EnsureActive(_) =>
  }
}
