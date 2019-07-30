/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

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
import com.lightbend.lagom.internal.cluster.ClusterMultiNodeConfig.node1
import com.typesafe.config.Config
import scala.concurrent.duration._

abstract class ClusteredMultiNodeUtils(val numOfNodes: Int)
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
      awaitAssert(Cluster(system).state.members.size should be(numOfNodes))
      awaitAssert(
        Cluster(system).state.members.toIndexedSeq.map(_.status).distinct should be(IndexedSeq(MemberStatus.Up))
      )
    }

    enterBarrier("startup")
  }

}
