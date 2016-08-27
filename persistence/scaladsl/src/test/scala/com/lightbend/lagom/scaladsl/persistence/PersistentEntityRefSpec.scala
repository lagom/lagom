/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import org.scalatest.WordSpec
import scala.collection.immutable
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import akka.actor.Address
import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.persistence.PersistenceSpec
import akka.actor.ActorSystem
import akka.cluster.Cluster
import java.io.File
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.testkit.TestKit
import com.softwaremill.macwire._
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import akka.pattern.AskTimeoutException
import com.lightbend.lagom.persistence.CorePersistentEntity.InvalidCommandException
import akka.Done
import com.lightbend.lagom.persistence.CorePersistentEntity.UnhandledCommandException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.time._

class PersistentEntityRefSpec extends WordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import PersistentEntityRefSpec._

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(100, Millis))

  var registry: PersistentEntityRegistry = _
  var system: ActorSystem = _

  override def beforeAll() = {
    val config = ConfigFactory.parseString("""
      |akka.actor.provider = akka.cluster.ClusterActorRefProvider
      |akka.remote.netty.tcp.port = 0
      |akka.remote.netty.tcp.hostname = 127.0.0.1
      |akka.loglevel = INFO""".stripMargin).withFallback(PersistenceSpec.config("PersistentEntityRefTest"))

    val sys = ActorSystem("PersistentEntityRefSpec", config)
    val cluster = Cluster(sys)
    cluster.join(cluster.selfAddress)

    val cassandraDirectory = new File(s"target/${sys.name}")
    CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, true, 0)
    PersistenceSpec.awaitPersistenceInit(sys)

    val reg = wire[PersistentEntityRegistry]
    reg.register[Cmd, Evt, State, TestEntity](() => wire[TestEntity]) // todo type parameter inference

    registry = reg
    system = sys
  }

  "persistent entity" should {
    "send commands to target entity" in {
      val ref1 = registry.refFor(classOf[TestEntity], "1")
      val reply1 = ref1.ask[Evt, Add](Add("a", 1)) // FIXME type inference
      reply1.futureValue shouldBe Appended("A")

      val ref2 = registry.refFor(classOf[TestEntity], "2")
      val reply2 = ref2.ask[Evt, Add](Add("b", 1)) // FIXME type inference
      reply2.futureValue shouldBe Appended("B")

      val reply3 = ref2.ask[Evt, Add](Add("c", 1)) // FIXME type inference
      reply3.futureValue shouldBe Appended("C")

      val state1 = ref1.ask[State, Get.type](Get) // FIXME type inference
      state1.futureValue.elements shouldBe immutable.Seq("A")

      val state2 = ref2.ask[State, Get.type](Get) // FIXME type inference
      state2.futureValue.elements shouldBe immutable.Seq("B", "C")
    }

    "throw ask timeout" in {
      val ref1 = registry.refFor(classOf[TestEntity], "10").withAskTimeout(1.millis)
      val replies = (0 to 100) map { _ =>
        ref1.ask[Evt, Add](Add("c", 1)) // FIXME type inference
      }

      intercept[AskTimeoutException] {
        import scala.concurrent.ExecutionContext.Implicits.global
        Await.result(Future.sequence(replies), 10.seconds)
      }
    }

    "reject invalid command" in {
      val ref = registry.refFor(classOf[TestEntity], "10")
      val future = ref.ask[Evt, Add](Add("", 1)) // FIXME type inference

      intercept[InvalidCommandException] {
        import scala.concurrent.ExecutionContext.Implicits.global
        Await.result(future, 10.seconds)
      }
    }

    "reject empty command" in {
      val ref = registry.refFor(classOf[TestEntity], "10")
      // null will trigger NPE
      val future = ref.ask[Evt, Add](Add(null, 1)) // FIXME type inference

      intercept[NullPointerException] {
        import scala.concurrent.ExecutionContext.Implicits.global
        Await.result(future, 10.seconds)
      }
    }

    "reject unhandled command" in {
      val ref = registry.refFor(classOf[TestEntity], "10")
      val future = ref.ask[Done, UndefinedCmd.type](UndefinedCmd) // FIXME type inference

      intercept[UnhandledCommandException] {
        import scala.concurrent.ExecutionContext.Implicits.global
        Await.result(future, 10.seconds)
      }
    }

    "reject unregistered entity" in {
      intercept[IllegalArgumentException] {
        registry.refFor(classOf[AnotherEntity], "10")
      }
    }
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
    CassandraLauncher.stop()
  }
}

object PersistentEntityRefSpec {

  sealed trait Mode
  object Mode {
    object Prepend extends Mode
    object Append extends Mode
  }

  class State(val mode: Mode, val elements: immutable.Seq[String]) {
    def add(elem: String): State = mode match {
      case Mode.Prepend => new State(mode, elem +: elements)
      case Mode.Append  => new State(mode, elements :+ elem)
    }

    def prependMode = new State(Mode.Prepend, elements)
    def appendMode = new State(Mode.Append, elements)
  }
  object State {
    final val Empty = new State(Mode.Append, Nil)
  }

  trait Cmd
  case class ChangeMode(mode: Mode) extends Cmd with ReplyType[Evt]
  case object Get extends Cmd with ReplyType[State]
  case object GetAddress extends Cmd with ReplyType[Address]
  case class Add(element: String, times: Int) extends Cmd with ReplyType[Evt]
  case object UndefinedCmd extends Cmd with ReplyType[Done]

  trait Evt
  case class Appended(element: String) extends Evt
  case class Prepended(element: String) extends Evt
  case object InAppendMode extends Evt
  case object InPrependMode extends Evt

  class TestEntity extends PersistentEntity[Cmd, Evt, State] {

    private val defaultCommandHandlers: PartialFunction[Any, Function[CommandContext[Any], Option[Persist[_ <: Evt]]]] = {
      case ChangeMode(mode) => ctx => mode match {
        case mode if state.mode == mode => Some(ctx.done())
        case Mode.Append                => Some(ctx.thenPersist(InAppendMode, ctx.reply))
        case Mode.Prepend               => Some(ctx.thenPersist(InPrependMode, ctx.reply))
      }
      case Get => ctx =>
        ctx.reply(state)
        Some(ctx.done())
      case GetAddress => ctx =>
        //ctx.reply(Cluster.get(system).selfAddress())
        Some(ctx.done())
    }

    private def addCommandHandler(eventFactory: String => Evt): PartialFunction[Any, Function[CommandContext[Any], Option[Persist[_ <: Evt]]]] = {
      case Add(element, times) => ctx =>
        if (element == null) {
          throw new NullPointerException() //SimulatedNullpointerException()
        }
        if (element.length == 0) {
          ctx.invalidCommand("element must not be empty")
          Some(ctx.done())
        } else {
          val a = eventFactory(element.toUpperCase())
          if (times == 1) {
            Some(ctx.thenPersist(a, ctx.reply))
          } else {
            Some(ctx.thenPersistAll(immutable.Seq.fill(times)(a), () => ctx.reply(a)))
          }
        }
    }

    private val defaultEventHandlers: PartialFunction[Any, Behavior] = {
      case Appended(el)  => behavior.transformState(_.add(el))
      case Prepended(el) => behavior.transformState(_.add(el))
      case InAppendMode  => buildBehavior(new Appended(_), state)
      case InPrependMode => buildBehavior(new Prepended(_), state)
    }

    private def buildBehavior(addEvent: String => Evt, state: State): Behavior = {
      val function: PartialFunction[Any, Function[CoreCommandContext[Any], Option[Persist[_ <: Evt]]]] = (defaultCommandHandlers orElse addCommandHandler(addEvent)).asInstanceOf[PartialFunction[Any, Function[CoreCommandContext[Any], Option[Persist[_ <: Evt]]]]]
      newBehaviorBuilder(state)
        .withCommandHandlers(function)
        .withEventHandlers(defaultEventHandlers)
        .build()
    }

    def initialBehavior(snapshotState: Option[State]): Behavior = {
      val state = snapshotState.getOrElse(State.Empty)
      val addEvent = state.mode match {
        case Mode.Append  => new Appended(_)
        case Mode.Prepend => new Prepended(_)
      }
      buildBehavior(addEvent, state)
    }
  }

  class AnotherEntity extends PersistentEntity[Cmd, Evt, State] {
    def initialBehavior(snapshotState: Option[State]) = ???
  }
}