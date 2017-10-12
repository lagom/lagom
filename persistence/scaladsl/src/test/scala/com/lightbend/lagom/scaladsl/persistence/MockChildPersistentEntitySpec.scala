/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.actor.{ Actor, Props }
import akka.testkit.TestProbe
import akka.util.Timeout
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

import scala.concurrent.duration._

class MockChildPersistentEntitySpec extends ActorSystemSpec {

  "The mock child persistent entity factory" should {
    "allow mock tells" in {
      val probe = TestProbe()
      val actor = system.actorOf(Props(new MyActor(ChildPersistentEntityFactory.mocked(probe.ref))))
      actor ! Tell
      probe.expectMsg(MockCommand)
      probe.reply("reply")
      expectMsg("reply")
    }
    "allow mock asks" in {
      val probe = TestProbe()
      val actor = system.actorOf(Props(new MyActor(ChildPersistentEntityFactory.mocked(probe.ref))))
      actor ! Ask
      probe.expectMsg(MockCommand)
      probe.reply("reply")
      expectMsg("reply")
    }
    "allow mock forwards" in {
      val probe = TestProbe()
      val actor = system.actorOf(Props(new MyActor(ChildPersistentEntityFactory.mocked(probe.ref))))
      actor ! Forward
      probe.expectMsg(MockCommand)
      probe.reply("reply")
      expectMsg("reply")
    }
    "allow stopping the entity" in {
      val probe = TestProbe()
      val actor = system.actorOf(Props(new MyActor(ChildPersistentEntityFactory.mocked(probe.ref))))
      watch(probe.ref)
      actor ! Stop
      expectTerminated(probe.ref)
    }
  }

  case object MockCommand extends ReplyType[String]
  case object Ask
  case object Tell
  case object Stop
  case object Forward

  private class MyActor(factory: ChildPersistentEntityFactory[MyEntity]) extends Actor {
    import akka.pattern.pipe
    import context.dispatcher
    implicit val timeout = Timeout(10.seconds)
    val entity = factory("foo", "foo")
    override def receive: Receive = {
      case Ask     => (entity ? MockCommand) pipeTo sender()
      case Tell    => entity.!(MockCommand)(sender())
      case Forward => entity forward MockCommand
      case Stop    => entity.stop()
    }
  }

  private class MyEntity extends PersistentEntity {
    override type Command = MockCommand.type
    override type Event = Any
    override type State = Any

    override def initialState = ()
    override def behavior = PartialFunction.empty
  }

}
