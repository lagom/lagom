/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.pattern.AskTimeoutException
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.{ ActorMaterializer, Materializer }
import akka.testkit.TestKit
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.{ PersistentEntity, PersistentEntityRegistry, TestEntity }
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.{ InvalidCommandException, UnhandledCommandException }
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Mode
import com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalactic.ConversionCheckedTripleEquals
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures

class PersistentEntityRefSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures with ConversionCheckedTripleEquals {

  val config: Config = ConfigFactory.parseString("""
      akka.actor.provider = akka.cluster.ClusterActorRefProvider
      akka.remote.netty.tcp.port = 0
      akka.remote.netty.tcp.hostname = 127.0.0.1
      akka.loglevel = INFO
      lagom.serialization.play-json.serializer-registry="com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry"
  """).withFallback(TestUtil.persistenceConfig("PersistentEntityRefTest", CassandraLauncher.randomPort))
  private val system: ActorSystem = ActorSystem("PersistentEntityRefSpec", config)

  override def beforeAll(): Unit = {

    Cluster.get(system).join(Cluster.get(system).selfAddress)
    val cassandraDirectory: File = new File("target/PersistentEntityRefTest")
    CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, true, 0)
    TestUtil.awaitPersistenceInit(system)
  }

  override def afterAll() {
    CassandraLauncher.stop()
    TestKit.shutdownActorSystem(system)
  }

  class AnotherEntity extends PersistentEntity {
    override type Command = Integer
    override type Event = String
    override type State = String

    def initialState: String = ""
    override def behavior = Actions()
  }

  val components = new CassandraPersistenceComponents {
    override def actorSystem: ActorSystem = system
    override def executionContext: ExecutionContext = system.dispatcher
    override def configuration: play.api.Configuration = play.api.Configuration(config)
    override def materializer: Materializer = ActorMaterializer()(system)
    override def serviceLocator: ServiceLocator = NoServiceLocator
  }

  private def registry: PersistentEntityRegistry = {
    val reg: PersistentEntityRegistry = components.persistentEntityRegistry
    reg.register(new TestEntity(system))
    reg
  }

  implicit val patience = PatienceConfig(5.seconds, 100.millis)

  "The Cassandra persistence backend" should {

    "send commands to the registry" in {
      val ref1 = registry.refFor[TestEntity]("1")
      ref1.ask(TestEntity.Add("a"))
        .futureValue(Timeout(15.seconds)) should ===(TestEntity.Appended("A"))

      val ref2 = registry.refFor[TestEntity]("2")

      ref2.ask(TestEntity.Add("b")).futureValue should ===(TestEntity.Appended("B"))

      ref2.ask(TestEntity.Add("c")).futureValue should ===(TestEntity.Appended("C"))

      ref1.ask(TestEntity.Get).futureValue should ===(TestEntity.State(Mode.Append, List("A")))

      ref2.ask(TestEntity.Get).futureValue should ===(TestEntity.State(Mode.Append, List("B", "C")))
    }

    "ask timeout when reply does not reply in time" in {
      val ref = registry.refFor[TestEntity]("10").withAskTimeout(1.millisecond)

      val replies =
        for (i <- 0 until 100) yield ref.ask(TestEntity.Add("c"))

      import scala.concurrent.ExecutionContext.Implicits.global
      Future.sequence(replies).failed.futureValue(Timeout(20.seconds)) shouldBe an[AskTimeoutException]
    }

    "fail future on invalid command" in {
      val ref = registry.refFor[TestEntity]("10")
      // empty not allowed
      ref.ask(TestEntity.Add("")).failed.futureValue shouldBe an[InvalidCommandException]
    }

    "fail future on error in entity" in {
      val ref = registry.refFor[TestEntity]("10")
      ref.ask(TestEntity.Add(null)).failed.futureValue shouldBe a[NullPointerException]
    }

    "fail future on unhandled command" in {
      val ref = registry.refFor[TestEntity]("10")
      ref.ask(TestEntity.UndefinedCmd).failed.futureValue shouldBe an[UnhandledCommandException]
    }

    "throw exception on unregistered entity" in {
      intercept[IllegalArgumentException] {
        registry.refFor[AnotherEntity]("whatever")
      }
    }

  }
}
