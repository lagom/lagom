/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.cassandra

import java.io.File

import akka.actor.setup.ActorSystemSetup
import akka.actor.ActorSystem
import akka.actor.BootstrapSetup
import akka.actor.CoordinatedShutdown
import akka.cluster.Cluster
import akka.pattern.AskTimeoutException
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.testkit.TestKit
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig.cassandraConfig
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.InvalidCommandException
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.UnhandledCommandException
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Mode
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.TestEntity
import com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll
import play.api.Environment
import play.api.{ Mode => PlayMode }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class PersistentEntityRefSpec
    extends AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with TypeCheckedTripleEquals {
  implicit override val patienceConfig = PatienceConfig(5.seconds, 150.millis)

  val config: Config =
    ConfigFactory
      .parseString("akka.loglevel = INFO")
      .withFallback(cassandraConfig("PersistentEntityRefTest", CassandraLauncher.randomPort))

  private val system: ActorSystem = ActorSystem(
    "PersistentEntityRefSpec",
    ActorSystemSetup(
      BootstrapSetup(config),
      JsonSerializerRegistry.serializationSetupFor(TestEntitySerializerRegistry)
    )
  )

  override def beforeAll(): Unit = {
    super.beforeAll()

    Cluster.get(system).join(Cluster.get(system).selfAddress)
    val cassandraDirectory: File = new File("target/PersistentEntityRefTest")
    CassandraLauncher.start(cassandraDirectory, "lagom-test-embedded-cassandra.yaml", true, 0)
    awaitPersistenceInit(system)
  }

  override def afterAll(): Unit = {
    CassandraLauncher.stop()
    TestKit.shutdownActorSystem(system)

    super.afterAll()
  }

  class AnotherEntity extends PersistentEntity {
    override type Command = Integer
    override type Event   = String
    override type State   = String

    def initialState: String = ""
    override def behavior    = Actions.empty
  }

  val components = new CassandraPersistenceComponents {
    override def actorSystem: ActorSystem                 = system
    override def executionContext: ExecutionContext       = system.dispatcher
    override def coordinatedShutdown: CoordinatedShutdown = CoordinatedShutdown(actorSystem)

    override def environment: Environment                       = Environment(new File("."), getClass.getClassLoader, PlayMode.Test)
    override def configuration: play.api.Configuration          = play.api.Configuration(config)
    override def materializer: Materializer                     = SystemMaterializer(actorSystem).materializer
    override def serviceLocator: ServiceLocator                 = NoServiceLocator
    override def jsonSerializerRegistry: JsonSerializerRegistry = TestEntitySerializerRegistry
  }

  private def registry: PersistentEntityRegistry = {
    val reg: PersistentEntityRegistry = components.persistentEntityRegistry
    reg.register(new TestEntity(system))
    reg
  }

  "The Cassandra persistence backend" should {
    "send commands to the registry" in {
      val ref1 = registry.refFor[TestEntity]("1")
      ref1
        .ask(TestEntity.Add("a"))
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
