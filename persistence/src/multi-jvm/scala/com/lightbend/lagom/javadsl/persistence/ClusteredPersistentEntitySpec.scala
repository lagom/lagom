/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.actor.{ Props, Actor }
import com.typesafe.config.ConfigFactory
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.persistence.journal.PersistencePluginProxy
import java.io.File
import akka.actor.Identify
import akka.actor.ActorIdentity
import akka.remote.testconductor.RoleName
import akka.cluster.Cluster
import akka.actor.Address
import akka.actor.ActorRef
import com.lightbend.lagom.javadsl.persistence.testkit.pipe
import com.lightbend.lagom.javadsl.cluster.testkit.ActorSystemModule
import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer
import akka.actor.ExtendedActorSystem
import com.google.inject.Guice
import akka.cluster.MemberStatus

object ClusteredPersistentEntityConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  commonConfig(ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"

    lagom.persistence.run-entities-on-role = backend

    akka.persistence.journal.plugin = "akka.persistence.journal.proxy"
    akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.proxy"

    akka.persistence.journal.proxy {
      target-journal-plugin = "cassandra-journal"
      init-timeout = 20s
    }
    akka.persistence.snapshot-store.proxy {
      target-snapshot-store-plugin = "cassandra-snapshot-store"
      init-timeout = 20s
    }

    cassandra-journal.session-provider = akka.persistence.cassandra.ConfigSessionProvider
    cassandra-snapshot-store.session-provider = akka.persistence.cassandra.ConfigSessionProvider

    # don't terminate in this test
    terminate-system-after-member-removed = 60s
    """))

  nodeConfig(node1) {
    ConfigFactory.parseString(s"""
      akka.cluster.roles = ["backend"]

      akka.persistence.journal.proxy.start-target-journal = on
      akka.persistence.snapshot-store.proxy.start-target-snapshot-store = on

      """).withFallback(PersistenceSpec.config("ClusteredPersistentEntitySpec"))
  }

  nodeConfig(node2) {
    ConfigFactory.parseString(s"""
      akka.cluster.roles = ["backend"]
      """)
  }

}

class ClusteredPersistentEntitySpecMultiJvmNode1 extends ClusteredPersistentEntitySpec
class ClusteredPersistentEntitySpecMultiJvmNode2 extends ClusteredPersistentEntitySpec
class ClusteredPersistentEntitySpecMultiJvmNode3 extends ClusteredPersistentEntitySpec

class ClusteredPersistentEntitySpec extends MultiNodeSpec(ClusteredPersistentEntityConfig)
  with STMultiNodeSpec with ImplicitSender {

  import ClusteredPersistentEntityConfig._

  override def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
    }
    enterBarrier(from.name + "-joined")
  }

  def fullAddress(ref: ActorRef): Address =
    if (ref.path.address.hasLocalScope) Cluster(system).selfAddress
    else ref.path.address

  override protected def atStartup() {
    runOn(node1) {
      val cassandraDirectory = new File("target/" + system.name)
      CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, clean = true, port = 0)
      PersistencePluginProxy.start(system)
      PersistenceSpec.awaitPersistenceInit(system)
    }
    enterBarrier("cassandra-started")
    PersistencePluginProxy.setTargetLocation(system, node(node1).address)
    enterBarrier("journal-initialized")

    roles.foreach(n => join(n, node1))
    within(15.seconds) {
      awaitAssert(Cluster(system).state.members.size should be(3))
      awaitAssert(Cluster(system).state.members.map(_.status) should be(Set(MemberStatus.Up)))
    }

    enterBarrier("startup")
  }

  override protected def afterTermination() {
    CassandraLauncher.stop()
  }

  val injector = Guice.createInjector(new ActorSystemModule(system), new PersistenceModule)

  val registry: PersistentEntityRegistry = {
    val reg = injector.getInstance(classOf[PersistentEntityRegistry])
    reg.register(classOf[TestEntity])
    reg
  }

  "A PersistentEntity in a Cluster" must {

    "send commands to target entity" in within(20.seconds) {

      val ref1 = registry.refFor(classOf[TestEntity], "1").withAskTimeout(remaining)

      // note that this is done on both node1 and node2
      val r1: CompletionStage[TestEntity.Evt] = ref1.ask(TestEntity.Add.of("a"))
      r1.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("A"))
      enterBarrier("appended-A")

      val ref2 = registry.refFor(classOf[TestEntity], "2")
      val r2: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("b"))
      r2.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("B"))
      enterBarrier("appended-B")

      val r3: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("c"))
      r3.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("C"))
      enterBarrier("appended-C")

      val r4: CompletionStage[TestEntity.State] = ref1.ask(TestEntity.Get.instance)
      r4.pipeTo(testActor)
      expectMsgType[TestEntity.State].getElements.asScala.toList should ===(List("A", "A", "A"))

      val r5: CompletionStage[TestEntity.State] = ref2.ask(TestEntity.Get.instance)
      r5.pipeTo(testActor)
      expectMsgType[TestEntity.State].getElements.asScala.toList should ===(List("B", "B", "B", "C", "C", "C"))

      enterBarrier("after-1")
    }

    "run entities on specific node roles" in {
      // node1 and node2 are configured with "backend" role
      // and lagom.persistence.run-entities-on-role = backend
      // i.e. no entities on node3

      val entities = for (n <- 10 to 29) yield registry.refFor(classOf[TestEntity], n.toString)
      val addresses = entities.map { ent =>
        val r: CompletionStage[Address] = ent.ask(TestEntity.GetAddress.instance)
        r.pipeTo(testActor)
        expectMsgType[Address]
      }.toSet

      addresses should not contain (node(node3).address)
      enterBarrier("after-2")
    }

    "have support for graceful leaving" in {
      runOn(node2) {
        registry.gracefulShutdown(20.seconds).toCompletableFuture().get(20, SECONDS)
      }
      enterBarrier("node2-left")

      runOn(node1) {
        within(15.seconds) {
          val ref1 = registry.refFor(classOf[TestEntity], "1").withAskTimeout(remaining)
          val r1: CompletionStage[TestEntity.Evt] = ref1.ask(TestEntity.Add.of("a"))
          r1.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("A"))

          val ref2 = registry.refFor(classOf[TestEntity], "2")
          val r2: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("b"))
          r2.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("B"))

          val r3: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("c"))
          r3.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("C"))
        }
      }

      enterBarrier("node1-working")
    }
  }
}

