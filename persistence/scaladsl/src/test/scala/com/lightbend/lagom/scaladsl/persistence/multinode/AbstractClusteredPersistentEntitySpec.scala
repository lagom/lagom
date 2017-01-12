/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.multinode

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ ActorRef, Address }
import akka.cluster.{ Cluster, MemberStatus }
import akka.pattern.pipe
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.ImplicitSender
import com.lightbend.lagom.scaladsl.persistence._
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.Environment

abstract class AbstractClusteredPersistentEntityConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  val databasePort = System.getProperty("database.port").toInt
  val environment = Environment.simple()

  commonConfig(additionalCommonConfig(databasePort).withFallback(ConfigFactory.parseString(
    """
      akka.loglevel = INFO
      lagom.persistence.run-entities-on-role = "backend"
      lagom.persistence.read-side.run-on-role = "read-side"
      terminate-system-after-member-removed = 60s
    """
  ).withFallback(ConfigFactory.parseResources("play/reference-overrides.conf"))))

  def additionalCommonConfig(databasePort: Int): Config

  nodeConfig(node1) {
    ConfigFactory.parseString(s"""
      akka.cluster.roles = ["backend", "read-side"]
      """)
  }

  nodeConfig(node2) {
    ConfigFactory.parseString(s"""
      akka.cluster.roles = ["backend"]
      """)
  }

  nodeConfig(node3) {
    ConfigFactory.parseString(
      s"""
       akka.cluster.roles = ["read-side"]
      """
    )
  }

}

abstract class AbstractClusteredPersistentEntitySpec(config: AbstractClusteredPersistentEntityConfig) extends MultiNodeSpec(config)
  with STMultiNodeSpec with ImplicitSender {

  import config._
  // implicit EC needed for pipeTo
  import system.dispatcher

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
    // Initialize components
    registry.register(new TestEntity(system))
    components.readSide.register(readSideProcessor())

    roles.foreach(n => join(n, node1))
    within(15.seconds) {
      awaitAssert(Cluster(system).state.members.size should be(3))
      awaitAssert(Cluster(system).state.members.map(_.status) should be(Set(MemberStatus.Up)))
    }

    enterBarrier("startup")
  }

  def components: PersistenceComponents

  def registry: PersistentEntityRegistry = components.persistentEntityRegistry

  protected def readSideProcessor: () => ReadSideProcessor[TestEntity.Evt]

  protected def getAppendCount(id: String): Future[Long]

  def expectAppendCount(id: String, expected: Long) = {
    runOn(node1) {
      within(20.seconds) {
        awaitAssert {
          val count = Await.result(getAppendCount(id), 5.seconds)
          count should ===(expected)
        }
      }
    }
  }

  "A PersistentEntity in a Cluster" must {

    "send commands to target entity" in within(20.seconds) {

      val ref1 = registry.refFor[TestEntity]("1").withAskTimeout(remaining)

      // note that this is done on both node1 and node2
      val r1 = ref1.ask(TestEntity.Add("a"))
      r1.pipeTo(testActor)
      expectMsg(TestEntity.Appended("A"))
      enterBarrier("appended-A")

      val ref2 = registry.refFor[TestEntity]("2")
      val r2 = ref2.ask(TestEntity.Add("b"))
      r2.pipeTo(testActor)
      expectMsg(TestEntity.Appended("B"))
      enterBarrier("appended-B")

      val r3: Future[TestEntity.Evt] = ref2.ask(TestEntity.Add("c"))
      r3.pipeTo(testActor)
      expectMsg(TestEntity.Appended("C"))
      enterBarrier("appended-C")

      val r4: Future[TestEntity.State] = ref1.ask(TestEntity.Get)
      r4.pipeTo(testActor)
      expectMsgType[TestEntity.State].elements should ===(List("A", "A", "A"))

      val r5 = ref2.ask(TestEntity.Get)
      r5.pipeTo(testActor)
      expectMsgType[TestEntity.State].elements should ===(List("B", "B", "B", "C", "C", "C"))

      expectAppendCount("1", 3)
      expectAppendCount("2", 6)

      enterBarrier("after-1")

    }

    "run entities on specific node roles" in {
      // node1 and node2 are configured with "backend" role
      // and lagom.persistence.run-entities-on-role = backend
      // i.e. no entities on node3

      val entities = for (n <- 10 to 29) yield registry.refFor[TestEntity](n.toString)
      val addresses = entities.map { ent =>
        val r = ent.ask(TestEntity.GetAddress)
        val h: Future[String] = r.map(_.hostPort) // compile check that the reply type is inferred correctly
        r.pipeTo(testActor)
        expectMsgType[Address]
      }.toSet

      addresses should not contain (node(node3).address)
      enterBarrier("after-2")
    }

    "have support for graceful leaving" in {
      runOn(node2) {
        Await.ready(registry.gracefulShutdown(20.seconds), 20.seconds)
      }
      enterBarrier("node2-left")

      runOn(node1) {
        within(15.seconds) {
          val ref1 = registry.refFor[TestEntity]("1").withAskTimeout(remaining)
          val r1: Future[TestEntity.Evt] = ref1.ask(TestEntity.Add("a"))
          r1.pipeTo(testActor)
          expectMsg(TestEntity.Appended("A"))

          val ref2 = registry.refFor[TestEntity]("2")
          val r2: Future[TestEntity.Evt] = ref2.ask(TestEntity.Add("b"))
          r2.pipeTo(testActor)
          expectMsg(TestEntity.Appended("B"))

          val r3: Future[TestEntity.Evt] = ref2.ask(TestEntity.Add("c"))
          r3.pipeTo(testActor)
          expectMsg(TestEntity.Appended("C"))
        }
      }

      enterBarrier("node1-working")
    }
  }
}
