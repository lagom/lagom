/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.multinode

import akka.actor.setup.ActorSystemSetup
import akka.actor.{ ActorRef, ActorSystem, Address, BootstrapSetup }
import akka.cluster.{ Cluster, MemberStatus }
import akka.pattern.pipe
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.ImplicitSender
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.{ Config, ConfigFactory }
import org.slf4j.LoggerFactory
import play.api.Environment

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

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

      # Don't terminate the actor system when doing a coordinated shutdown
      # See http://doc.akka.io/docs/akka/2.5.0/project/migration-guide-2.4.x-2.5.x.html#Coordinated_Shutdown
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
    """
  ).withFallback(ConfigFactory.parseResources("play/reference-overrides.conf"))))

  def additionalCommonConfig(databasePort: Int): Config

  nodeConfig(node1) {
    ConfigFactory.parseString("""akka.cluster.roles = ["backend", "read-side"]""")
  }

  nodeConfig(node2) {
    ConfigFactory.parseString("""akka.cluster.roles = ["backend"]""")
  }

  nodeConfig(node3) {
    ConfigFactory.parseString("""akka.cluster.roles = ["read-side"]""")
  }

}

object AbstractClusteredPersistentEntitySpec {
  // Copied from MultiNodeSpec
  private def getCallerName(clazz: Class[_]): String = {
    val s = Thread.currentThread.getStackTrace map (_.getClassName) drop 1 dropWhile (_ matches ".*MultiNodeSpec.?$")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

  def createActorSystem(jsonSerializerRegistry: JsonSerializerRegistry): (Config) => ActorSystem = { config =>
    val setup = ActorSystemSetup(
      BootstrapSetup(ConfigFactory.load(config)),
      JsonSerializerRegistry.serializationSetupFor(jsonSerializerRegistry)
    )
    ActorSystem(getCallerName(classOf[MultiNodeSpec]), setup)
  }

}

abstract class AbstractClusteredPersistentEntitySpec(config: AbstractClusteredPersistentEntityConfig)
  extends MultiNodeSpec(config, AbstractClusteredPersistentEntitySpec.createActorSystem(TestEntitySerializerRegistry))
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

  /**
   * uses overridden {{getAppendCount}} to assert a given entity {{id}} emited the {{expected}} number of events. The
   * implementation uses polling from only node1 so nodes 2 and 3 will skip this code.
   */
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

    "send commands to target entity" in within(21.seconds) {
      // this barrier at the beginning of the test will be run on all nodes and should be at the
      // beginning of the test to ensure it's run.
      enterBarrier("before-1")

      val ref1 = registry.refFor[TestEntity]("1").withAskTimeout(remaining)
      val ref2 = registry.refFor[TestEntity]("2")

      // STEP 1: send some commands from all nodes of the test to ref1 and ref2
      // note that this is done on node1, node2 and node 3 !!
      val r1 = ref1.ask(TestEntity.Add("a"))
      r1.pipeTo(testActor)
      expectMsg(TestEntity.Appended("A"))
      enterBarrier("appended-A")

      val r2 = ref2.ask(TestEntity.Add("b"))
      r2.pipeTo(testActor)
      expectMsg(TestEntity.Appended("B"))
      enterBarrier("appended-B")

      val r3: Future[TestEntity.Evt] = ref2.ask(TestEntity.Add("c"))
      r3.pipeTo(testActor)
      expectMsg(TestEntity.Appended("C"))
      enterBarrier("appended-C")

      // STEP 2: assert both ref's stored all the commands in their respective state.
      val r4: Future[TestEntity.State] = ref1.ask(TestEntity.Get)
      r4.pipeTo(testActor)
      // There are three events of each because the Commands above are executed on all 3 nodes of the multi-jvm test
      expectMsgType[TestEntity.State].elements should ===(List("A", "A", "A"))

      val r5 = ref2.ask(TestEntity.Get)
      r5.pipeTo(testActor)
      expectMsgType[TestEntity.State].elements should ===(List("B", "B", "B", "C", "C", "C"))

      // STEP 3: assert the number of events consumed in the read-side processors equals the number of expected events.
      // NOTE: in nodes node2 and node3 {{expectAppendCount}} is a noop
      expectAppendCount("1", 3)
      expectAppendCount("2", 6)

    }

    "run entities on specific node roles" in {
      // this barrier at the beginning of the test will be run on all nodes and should be at the
      // beginning of the test to ensure it's run.
      enterBarrier("before-2")
      // node1 and node2 are configured with "backend" role
      // and lagom.persistence.run-entities-on-role = backend
      // i.e. no entities on node3

      val entities = for (n <- 10 to 29) yield registry.refFor[TestEntity](n.toString)
      val addresses = entities.map {
        ent =>
          val r = ent.ask(TestEntity.GetAddress)
          val h: Future[String] = r.map(_.hostPort) // compile check that the reply type is inferred correctly
          r.pipeTo(testActor)
          expectMsgType[Address]
      }.toSet

      addresses should not contain (node(node3).address)
    }

    "have support for graceful leaving" in {
      // this barrier at the beginning of the test will be run on all nodes and should be at the
      // beginning of the test to ensure it's run.
      enterBarrier("before-3")

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

          val ref2 = registry.refFor[TestEntity]("2").withAskTimeout(remaining)
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
