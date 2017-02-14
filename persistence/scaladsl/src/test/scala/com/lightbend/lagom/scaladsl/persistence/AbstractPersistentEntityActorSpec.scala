/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.actor.{ Actor, Props }
import akka.cluster.sharding.ShardRegion
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.scaladsl.persistence.PersistentEntityActor
import com.lightbend.lagom.persistence.ActorSystemSpec

import scala.concurrent.duration._

object AbstractPersistentEntityActorSpec {
  class TestPassivationParent extends Actor {

    val child = context.actorOf(PersistentEntityActor.props("test", Some("1"),
      () => new TestEntity(context.system), None, 1.second))

    def receive = {
      case ShardRegion.Passivate(stopMsg) =>
        sender() ! stopMsg
      case msg =>
        child.forward(msg)
    }
  }
}

trait AbstractPersistentEntityActorSpec { spec: ActorSystemSpec =>

  "PersistentEntityActor" must {
    "persist events" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Some("1"),
        () => new TestEntity(system), None, 10.seconds))
      p ! TestEntity.Get
      val state = expectMsgType[TestEntity.State]
      state.elements.size should ===(0)
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.Add("c")
      expectMsg(TestEntity.Appended("C"))
      p ! TestEntity.Get
      val state2 = expectMsgType[TestEntity.State]
      state2.elements should ===(List("A", "B", "C"))

      // start another with same persistenceId should recover state
      val p2 = system.actorOf(PersistentEntityActor.props("test", Some("1"),
        () => new TestEntity(system), None, 10.seconds))
      p2 ! TestEntity.Get
      val state3 = expectMsgType[TestEntity.State]
      state3.elements should ===(List("A", "B", "C"))
    }

    "be able to change behavior" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Some("2"),
        () => new TestEntity(system), None, 10.seconds))
      p ! TestEntity.Get
      val state = expectMsgType[TestEntity.State]
      state.mode should ===(TestEntity.Mode.Append)
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.ChangeMode(TestEntity.Mode.Prepend)
      expectMsg(TestEntity.InPrependMode)
      p ! TestEntity.Add("C")
      expectMsg(TestEntity.Prepended("c"))
      p ! TestEntity.Get
      val state2 = expectMsgType[TestEntity.State]
      state2.elements should ===(List("c", "A", "B"))

      // start another with same persistenceId should recover state
      val p2 = system.actorOf(PersistentEntityActor.props("test", Some("2"),
        () => new TestEntity(system), None, 10.seconds))
      p2 ! TestEntity.Get
      val state3 = expectMsgType[TestEntity.State]
      state3.mode should ===(TestEntity.Mode.Prepend)
      state3.elements should ===(List("c", "A", "B"))
      p2 ! TestEntity.Add("D")
      expectMsg(TestEntity.Prepended("d"))
      p2 ! TestEntity.Get
      val state4 = expectMsgType[TestEntity.State]
      state4.elements should ===(List("d", "c", "A", "B"))

      p2 ! TestEntity.ChangeMode(TestEntity.Mode.Append)
      expectMsg(TestEntity.InAppendMode)
      p2 ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))
      p2 ! TestEntity.Get
      val state5 = expectMsgType[TestEntity.State]
      state5.elements should ===(List("d", "c", "A", "B", "E"))
    }

    "notify when recovery is completed" in {
      val probe = TestProbe()
      val p = system.actorOf(PersistentEntityActor.props("test", Some("3"),
        () => new TestEntity(system, Some(probe.ref)), None, 10.seconds))
      probe.expectMsgType[TestEntity.AfterRecovery]
    }

    "save snapshots" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Some("4"),
        () => new TestEntity(system), Some(3), 10.seconds))
      for (n <- 1 to 10) {
        p ! TestEntity.Add(n.toString)
        expectMsg(TestEntity.Appended(n.toString))
      }

      // start another with same persistenceId should recover state
      // awaitAssert because it is not guaranteed that we will see the snapshot immediately
      within(10.seconds) {
        awaitAssert {
          val probe2 = TestProbe()
          val p2 = system.actorOf(PersistentEntityActor.props("test", Some("4"),
            () => new TestEntity(system, Some(probe2.ref)), Some(3), 10.seconds))
          val state2 = probe2.expectMsgType[TestEntity.AfterRecovery].state
          state2.elements should ===((1 to 10).toList.map(_.toString))
        }
      }
    }

    "persist several events from one command" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Some("5"),
        () => new TestEntity(system), None, 10.seconds))
      p ! TestEntity.Add("a", 3)
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Get
      val state2 = expectMsgType[TestEntity.State]
      state2.elements should ===(List("A", "A", "A"))
    }

    "passivate after idle" in {
      val p = system.actorOf(Props[AbstractPersistentEntityActorSpec.TestPassivationParent])
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      val entity = lastSender
      watch(entity)
      expectTerminated(entity)
    }

  }

}
