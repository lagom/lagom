/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.multinode

import java.util.concurrent.CompletionStage

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.javadsl.persistence.testkit.pipe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import play.api.inject.Injector
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import play.api.Configuration
import play.api.Environment

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import com.lightbend.lagom.internal.cluster.STMultiNodeSpec

abstract class AbstractClusteredPersistentEntityConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  val databasePort = System.getProperty("javadsl.database.port").toInt
  val environment  = Environment.simple()

  commonConfig(
    additionalCommonConfig(databasePort).withFallback(
      ConfigFactory
        .parseString(
          """
      akka.loglevel = INFO
      lagom.persistence.run-entities-on-role = "backend"
      lagom.persistence.read-side.run-on-role = "read-side"
      terminate-system-after-member-removed = 60s

      # increase default timeouts to leave wider margin for Travis.
      akka.testconductor.barrier-timeout=90s

      ## use coprime values for the timeouts below because it'll be easier to spot interferences.
      ## Also, make Akka's `single-expect-default` timeout higher since this test often `expect`'s over an ask operation.
      ## NOTE: these values used to be '9s' and '11s' but '9s' triggered timeouts quite often in Travis. If '13s'
      ## also triggers timeouts in Travis it's possible there's something worth reviewing on this test.
      lagom.persistence.ask-timeout = 13s
      akka.test.single-expect-default = 15s
      lagom.persistence.read-side.offset-timeout = 17s

      # Don't terminate the actor system when doing a coordinated shutdown
      # See http://doc.akka.io/docs/akka/2.6/project/migration-guide-2.4.x-2.5.x.html#Coordinated_Shutdown
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off

      # multi-jvm tests forms the cluster programmatically
      # therefore we disable Akka Cluster Bootstrap
      lagom.cluster.bootstrap.enabled = off

      # no jvm exit on tests
      lagom.cluster.exit-jvm-when-system-terminated = off

      akka.cluster.sharding.waiting-for-state-timeout = 5s

      # make sure ensure active kicks in fast enough on tests
      lagom.persistence.cluster.distribution.ensure-active-interval = 2s

    """
        )
        .withFallback(ConfigFactory.parseResources("play/reference-overrides.conf"))
    )
  )

  def additionalCommonConfig(databasePort: Int): Config

  nodeConfig(node1) {
    ConfigFactory.parseString("""akka.cluster.roles = ["backend", "read-side"]""")
  }

  nodeConfig(node2) {
    ConfigFactory.parseString("""
      akka.cluster.roles = ["backend"]
      cassandra-journal.keyspace-autocreate = false
      """.stripMargin)
  }

  nodeConfig(node3) {
    ConfigFactory.parseString("""
      akka.cluster.roles = ["read-side"]
      cassandra-journal.keyspace-autocreate = false
      """.stripMargin)
  }
}

abstract class AbstractClusteredPersistentEntitySpec(config: AbstractClusteredPersistentEntityConfig)
    extends MultiNodeSpec(config)
    with STMultiNodeSpec
    with ImplicitSender {
  import config._

  final def dilatedTimeout = testConductor.Settings.BarrierTimeout.duration

  override def initialParticipants: Int = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system).join(node(to).address)
    }
    enterBarrier(dilatedTimeout, from.name + "-joined")
  }

  def fullAddress(ref: ActorRef): Address =
    if (ref.path.address.hasLocalScope) Cluster(system).selfAddress
    else ref.path.address

  protected override def atStartup(): Unit = {
    // Initialize read side
    readSide

    roles.foreach(n => join(n, node1))
    within(15.seconds) {
      awaitAssert(Cluster(system).state.members.size should be(3))
      awaitAssert(
        Cluster(system).state.members.toIndexedSeq.map(_.status).distinct should be(IndexedSeq(MemberStatus.Up))
      )
    }

    enterBarrier(dilatedTimeout, "startup")
  }

  protected override def afterTermination(): Unit = {
    Await.ready(injector.instanceOf[Application].stop(), shutdownTimeout)
  }

  lazy val injector: Injector = {
    val configuration = Configuration(system.settings.config)
    new GuiceApplicationBuilder()
      .loadConfig(configuration)
      .overrides(bind[ActorSystem].toInstance(system))
      .build()
      .injector
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

  /**
   * uses overridden {{getAppendCount}} to assert a given entity {{id}} emitted the {{expected}} number of events. The
   * implementation uses polling from only node1 so nodes 2 and 3 will skip this code.
   */
  def expectAppendCount(id: String, expected: Long): Unit = {
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
    "send commands to target entity" in within(75.seconds) {
      // this barrier at the beginning of the test will be run on all nodes and should be at the
      // beginning of the test to ensure it's run.
      enterBarrier(dilatedTimeout, "start 'send commands to target entity'")

      val ref1 = registry.refFor(classOf[TestEntity], "entity-1")
      val ref2 = registry.refFor(classOf[TestEntity], "entity-2")

      // STEP 1: send some commands from all nodes of the test to ref1 and ref2
      // note that this is done on node1, node2 and node 3 !!
      val r1: CompletionStage[TestEntity.Evt] = ref1.ask(TestEntity.Add.of("a"))
      r1.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("entity-1", "A"))
      enterBarrier(dilatedTimeout, "appended-A")

      val r2: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("b"))
      r2.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("entity-2", "B"))
      enterBarrier(dilatedTimeout, "appended-B")

      val r3: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("c"))
      r3.pipeTo(testActor)
      expectMsg(new TestEntity.Appended("entity-2", "C"))
      enterBarrier(dilatedTimeout, "appended-C")

      // STEP 2: assert both ref's stored all the commands in their respective state.
      val r4: CompletionStage[TestEntity.State] = ref1.ask(TestEntity.Get.instance)
      r4.pipeTo(testActor)
      // There are three events of each because the Commands above are executed on all 3 nodes of the multi-jvm test
      expectMsgType[TestEntity.State].getElements.asScala.toList should ===(List("A", "A", "A"))

      val r5: CompletionStage[TestEntity.State] = ref2.ask(TestEntity.Get.instance)
      r5.pipeTo(testActor)
      expectMsgType[TestEntity.State].getElements.asScala.toList should ===(List("B", "B", "B", "C", "C", "C"))

      // STEP 3: assert the number of events consumed in the read-side processors equals the number of expected events.
      // NOTE: in nodes node2 and node3 {{expectAppendCount}} is a noop
      expectAppendCount("entity-1", 3)
      expectAppendCount("entity-2", 6)

      enterBarrier(dilatedTimeout, "end 'send commands to target entity'")
    }

    "run entities on specific node roles" in {
      // this barrier at the beginning of the test will be run on all nodes and should be at the
      // beginning of the test to ensure it's run.
      enterBarrier(dilatedTimeout, "start 'run entities on specific node roles'")
      // node1 and node2 are configured with "backend" role
      // and lagom.persistence.run-entities-on-role = backend
      // i.e. no entities on node3

      val entities = for (n <- 10 to 29) yield registry.refFor(classOf[TestEntity], n.toString)
      val addresses = entities.map { ent =>
        val r: CompletionStage[Address] = ent.ask(TestEntity.GetAddress.instance)
        r.pipeTo(testActor)
        expectMsgType[Address]
      }.toSet

      addresses should not contain node(node3).address

      enterBarrier(dilatedTimeout, "end 'run entities on specific node roles'")
    }

    "have support for graceful leaving" in {
      // this barrier at the beginning of the test will be run on all nodes and should be at the
      // beginning of the test to ensure it's run.
      enterBarrier(dilatedTimeout, "start 'have support for graceful leaving'")

      enterBarrier(dilatedTimeout, "node2-left")

      runOn(node1) {
        within(35.seconds) {
          val ref1                                = registry.refFor(classOf[TestEntity], "entity-1")
          val r1: CompletionStage[TestEntity.Evt] = ref1.ask(TestEntity.Add.of("a"))
          r1.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("entity-1", "A"))

          val ref2                                = registry.refFor(classOf[TestEntity], "entity-2")
          val r2: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("b"))
          r2.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("entity-2", "B"))

          val r3: CompletionStage[TestEntity.Evt] = ref2.ask(TestEntity.Add.of("c"))
          r3.pipeTo(testActor)
          expectMsg(new TestEntity.Appended("entity-2", "C"))
        }
      }

      enterBarrier(dilatedTimeout, "end 'have support for graceful leaving'")
    }
  }
}
