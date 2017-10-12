/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.Optional

import akka.actor.{ Actor, ActorRef, Props }
import akka.testkit.TestProbe
import akka.util.Timeout
import com.lightbend.lagom.javadsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.persistence.ActorSystemSpec

import scala.concurrent.duration._
import scala.compat.java8.FutureConverters

class MockChildPersistentEntitySpec extends ActorSystemSpec {

  "The mock child persistent entity factory" should {
    "allow mock tells" in {
      val probe = TestProbe()
      val actor = createActor(probe.ref)
      actor ! Tell
      probe.expectMsg(MockCommand)
      probe.reply("reply")
      expectMsg("reply")
    }
    "allow mock asks" in {
      val probe = TestProbe()
      val actor = createActor(probe.ref)
      actor ! Ask
      probe.expectMsg(MockCommand)
      probe.reply("reply")
      expectMsg("reply")
    }
    "allow mock forwards" in {
      val probe = TestProbe()
      val actor = createActor(probe.ref)
      actor ! Forward
      probe.expectMsg(MockCommand)
      probe.reply("reply")
      expectMsg("reply")
    }
    "allow stopping the entity" in {
      val probe = TestProbe()
      val actor = createActor(probe.ref)
      watch(probe.ref)
      actor ! Stop
      expectTerminated(probe.ref)
    }
  }

  private def createActor(testProbe: ActorRef): ActorRef = {
    system.actorOf(Props(new MyActor(ChildPersistentEntityFactory.mocked(classOf[MyEntity], testProbe))))
  }

  case object MockCommand extends ReplyType[String]

  case object Ask
  case object Tell
  case object Stop
  case object Forward

  private class MyActor(factory: ChildPersistentEntityFactory[MockCommand.type]) extends Actor {

    import akka.pattern.pipe
    import context.dispatcher

    private val timeout = Timeout(10.seconds)
    private val entity = factory.create("entity-id", "entity", context)
    context.watch(entity.getActor)

    override def receive: Receive = {
      case Ask     => FutureConverters.toScala(entity.ask[String, MockCommand.type](MockCommand, timeout)) pipeTo sender()
      case Tell    => entity.tell(MockCommand, sender())
      case Forward => entity.forward(MockCommand, context)
      case Stop    => entity.stop()
    }
  }

  private class MyEntity extends PersistentEntity[MockCommand.type, Any, Any] {
    override def initialBehavior(snapshotState: Optional[Any]): Behavior = newBehavior(())
  }
}

