/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import akka.pattern._
import org.scalatest.AsyncFlatSpec
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.{CurrentShardRegionState, GetShardRegionState}
import akka.util.Timeout

import scala.util.{Failure, Success}

class ClusterDistributionSpecMultiJvmNode1 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode2 extends ClusterDistributionSpec
class ClusterDistributionSpecMultiJvmNode3 extends ClusterDistributionSpec

class ClusterDistributionSpec extends ClusteredMultiNodeUtils(numOfNodes = 3) with ScalaFutures with Eventually {



  private val ensureActiveInterval: FiniteDuration = 1.second
  val distributionSettings: ClusterDistributionSettings =
    ClusterDistributionSettings(system)
      .copy(ensureActiveInterval = ensureActiveInterval)


  "A ClusterDistribution" must {

    "distribute the entityIds across nodes (so all nodes get a response)" in  {

      val numOfEntities = 20
      val minimalShardsPerNode = numOfEntities / numOfNodes

      val typeName = "CDTest"
      // There'll be 3 nodes in this test and each node will host a TestProbe.
      // Cluster Distribution on each node will create a `FakeActor.props` pointing
      // back to its own TestProbe. We request the creation of 10 FakeActor to ClusterDistribution
      // with the expectation that there'll be 3 or 4 FakeActor's for each TestProbe.
      val props: Props = FakeActor.props
      val entityIds    = (1 to numOfEntities).map(i => s"test-entity-id-$i").toSet

      // Load the extension and wait for other nodes to be ready before proceeding
      val cdExtension  = ClusterDistribution(system)
      enterBarrier("cluster-distribution-extension-is-loaded")

      val shardRegion =
          cdExtension.start(
          typeName,
          props,
          entityIds,
          distributionSettings
        )

      implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 200.millis)
      eventually {
        val shardRegionState =
          shardRegion
            .ask(GetShardRegionState)(3.seconds)
            .mapTo[CurrentShardRegionState].futureValue

        shardRegionState.shards.size should be >= minimalShardsPerNode
      }
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
