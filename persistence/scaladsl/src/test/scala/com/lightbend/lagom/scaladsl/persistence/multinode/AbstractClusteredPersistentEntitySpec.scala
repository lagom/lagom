/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.multinode

import akka.actor.setup.ActorSystemSetup

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{ ActorRef, ActorSystem, Address, BootstrapSetup }
import akka.cluster.{ Cluster, MemberStatus }
import akka.pattern.pipe
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.serialization.{ SerializationSetup, SerializerDetails }
import akka.testkit.ImplicitSender
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.playjson.{ JsonSerializerRegistry, PlayJsonSerializer }
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.Environment

import scala.collection.immutable

abstract class AbstractClusteredPersistentEntityConfig {

  private var _commonConf: Option[Config] = None
  private var _nodeConf = Map[RoleName, Config]()
  private var _roles = Vector[RoleName]()
  private var _deployments = Map[RoleName, immutable.Seq[String]]()
  private var _allDeploy = Vector[String]()
  private var _testTransport = false

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

  // The rest of this class is copied from MultiNodeConfig due to https://github.com/akka/akka/issues/22180
  /**
   * Register a common base config for all test participants, if so desired.
   */
  def commonConfig(config: Config): Unit = _commonConf = Some(config)

  /**
   * Register a config override for a specific participant.
   */
  def nodeConfig(roles: RoleName*)(configs: Config*): Unit = {
    val c = configs.reduceLeft(_ withFallback _)
    _nodeConf ++= roles map { _ → c }
  }

  /**
   * Include for verbose debug logging
   * @param on when `true` debug Config is returned, otherwise config with info logging
   */
  def debugConfig(on: Boolean): Config =
    if (on)
      ConfigFactory.parseString("""
        akka.loglevel = DEBUG
        akka.remote {
          log-received-messages = on
          log-sent-messages = on
        }
        akka.remote.artery {
          log-received-messages = on
          log-sent-messages = on
        }
        akka.actor.debug {
          receive = on
          fsm = on
        }
        akka.remote.log-remote-lifecycle-events = on
        """)
    else
      ConfigFactory.empty

  /**
   * Construct a RoleName and return it, to be used as an identifier in the
   * test. Registration of a role name creates a role which then needs to be
   * filled.
   */
  def role(name: String): RoleName = {
    if (_roles exists (_.name == name)) throw new IllegalArgumentException("non-unique role name " + name)
    val r = RoleName(name)
    _roles :+= r
    r
  }

  def deployOn(role: RoleName, deployment: String): Unit =
    _deployments += role → ((_deployments get role getOrElse Vector()) :+ deployment)

  def deployOnAll(deployment: String): Unit = _allDeploy :+= deployment

  /**
   * To be able to use `blackhole`, `passThrough`, and `throttle` you must
   * activate the failure injector and throttler transport adapters by
   * specifying `testTransport(on = true)` in your MultiNodeConfig.
   */
  def testTransport(on: Boolean): Unit = _testTransport = on

  lazy val myself: RoleName = {
    require(_roles.size > MultiNodeSpec.selfIndex, "not enough roles declared for this test")
    _roles(MultiNodeSpec.selfIndex)
  }

  def config: Config = {
    val transportConfig =
      if (_testTransport) ConfigFactory.parseString(
        """
           akka.remote.netty.tcp.applied-adapters = [trttl, gremlin]
           akka.remote.artery.advanced.test-mode = on
        """
      )
      else ConfigFactory.empty

    val configs = (_nodeConf get myself).toList ::: _commonConf.toList ::: transportConfig :: mnsNodeConfig :: mnsBaseConfig :: Nil
    configs reduceLeft (_ withFallback _)
  }

  def deployments(node: RoleName): immutable.Seq[String] = (_deployments get node getOrElse Nil) ++ _allDeploy

  def roles: immutable.Seq[RoleName] = _roles

  // Copied from MultiNodeSpec
  private def mapToConfig(map: Map[String, Any]): Config = {
    import scala.collection.JavaConverters._
    ConfigFactory.parseMap(map.asJava)
  }

  private val mnsNodeConfig = mapToConfig(Map(
    "akka.actor.provider" → "remote",
    "akka.remote.artery.canonical.hostname" → MultiNodeSpec.selfName,
    "akka.remote.netty.tcp.hostname" → MultiNodeSpec.selfName,
    "akka.remote.netty.tcp.port" → MultiNodeSpec.selfPort,
    "akka.remote.artery.canonical.port" → MultiNodeSpec.selfPort
  ))

  private val mnsBaseConfig: Config = ConfigFactory.parseString("""
      akka {
        loggers = ["akka.testkit.TestEventListener"]
        loglevel = "WARNING"
        stdout-loglevel = "WARNING"
        actor {
          default-dispatcher {
            executor = "fork-join-executor"
            fork-join-executor {
              parallelism-min = 8
              parallelism-factor = 2.0
              parallelism-max = 8
            }
          }
        }
      }
      """)
}

object AbstractClusteredPersistentEntitySpec {
  // Copied from MultiNodeSpec
  private def getCallerName(clazz: Class[_]): String = {
    val s = Thread.currentThread.getStackTrace map (_.getClassName) drop 1 dropWhile (_ matches ".*MultiNodeSpec.?$")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 ⇒ s
      case z  ⇒ s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

  def createActorSystem(config: AbstractClusteredPersistentEntityConfig, jsonSerializerRegistry: JsonSerializerRegistry): ActorSystem = {
    val setup = ActorSystemSetup(
      BootstrapSetup(ConfigFactory.load(config.config)),
      JsonSerializerRegistry.serializationSetupFor(jsonSerializerRegistry)
    )
    ActorSystem(getCallerName(classOf[MultiNodeSpec]), setup)
  }

}

abstract class AbstractClusteredPersistentEntitySpec(config: AbstractClusteredPersistentEntityConfig)
  extends MultiNodeSpec(config.myself, AbstractClusteredPersistentEntitySpec.createActorSystem(config, TestEntitySerializerRegistry),
    config.roles, config.deployments)
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
