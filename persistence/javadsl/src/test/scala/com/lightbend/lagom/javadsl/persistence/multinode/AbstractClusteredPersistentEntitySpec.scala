/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.multinode

import java.util.concurrent.CompletionStage

import akka.actor.{ ActorRef, ActorSystem, Address }
import akka.cluster.{ Cluster, MemberStatus }
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.ImplicitSender
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.javadsl.persistence.testkit.pipe
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{ Application, Configuration, Environment }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._
import play.api.inject.bind

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
    // Initialize read side
    readSide

    roles.foreach(n => join(n, node1))
    within(15.seconds) {
      awaitAssert(Cluster(system).state.members.size should be(3))
      awaitAssert(Cluster(system).state.members.map(_.status) should be(Set(MemberStatus.Up)))
    }

    enterBarrier("startup")
  }

  override protected def afterTermination(): Unit = {
    injector.instanceOf[Application].stop()
  }

  lazy val injector = {
    val configuration = Configuration(system.settings.config)
    new GuiceApplicationBuilder()
      .loadConfig(configuration)
      .overrides(bind[ActorSystem].toInstance(system))
      .build().injector
  }

  lazy val registry: PersistentEntityRegistry = {
    val reg = injector.instanceOf[PersistentEntityRegistry]
    reg.register(classOf[TestEntity])
    reg
  }

  protected def readSideProcessor: Class[_ <: ReadSideProcessor[TestEntity.Evt]]

  // lazy because we don't want to create it until after database is started
  lazy val readSide: ReadSide = {
    val rs = injector.instanceOf[ReadSide]
    rs.register(readSideProcessor)
    rs
  }

  protected def getAppendCount(id: String): CompletionStage[java.lang.Long]

  def expectAppendCount(id: String, expected: Long) = {
    runOn(node1) {
      within(20.seconds) {
        awaitAssert {
          val count = Await.result(getAppendCount(id).toScala, 5.seconds)
          count should ===(expected)
        }
      }
    }
  }

  "A PersistentEntity in a Cluster" must {

    "send commands to target entity" in within(20.seconds) {

      val ref1 = registry.refFor(classOf[TestEntity], "1").withAskTimeout(remaining)

      // note that this is done on both node1 and node2
      val r1: CompletionStage[TestEntity.Evt] = ref1.ask(TestEntity.Add.of("a"))
      r1.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("1", "A"))
      enterBarrier("appended-A")

      val ref2 = registry.refFor(classOf[TestEntity], "2")
      val r2: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("b"))
      r2.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("2", "B"))
      enterBarrier("appended-B")

      val r3: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("c"))
      r3.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("2", "C"))
      enterBarrier("appended-C")

      val r4: CompletionStage[TestEntity.State] = ref1.ask(TestEntity.Get.instance)
      r4.pipeTo(testActor)
      expectMsgType[TestEntity.State].getElements.asScala.toList should ===(List("A", "A", "A"))

      val r5: CompletionStage[TestEntity.State] = ref2.ask(TestEntity.Get.instance)
      r5.pipeTo(testActor)
      expectMsgType[TestEntity.State].getElements.asScala.toList should ===(List("B", "B", "B", "C", "C", "C"))

      expectAppendCount("1", 3)
      expectAppendCount("2", 6)

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
          expectMsg(new TestEntity.Appended("1", "A"))

          val ref2 = registry.refFor(classOf[TestEntity], "2")
          val r2: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("b"))
          r2.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("2", "B"))

          val r3: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("c"))
          r3.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("2", "C"))
        }
      }

      enterBarrier("node1-working")
    }
  }
}
