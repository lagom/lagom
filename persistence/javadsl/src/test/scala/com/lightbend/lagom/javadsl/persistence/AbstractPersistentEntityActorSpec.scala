/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import java.util.Optional

import akka.testkit.{ ImplicitSender, TestProbe }
import akka.actor.Actor
import akka.cluster.sharding.ShardRegion
import akka.actor.Props
import com.lightbend.lagom.internal.javadsl.persistence.PersistentEntityActor
import org.scalatest.WordSpecLike
import com.lightbend.lagom.persistence.ActorSystemSpec

object AbstractPersistentEntityActorSpec {
  class TestPassivationParent extends Actor {

    val child = context.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
      () => new TestEntity(context.system), Optional.empty(), 1.second))

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
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! TestEntity.Get.instance
      val state = expectMsgType[TestEntity.State]
      state.getElements.size should ===(0)
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("1", "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("1", "B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended("1", "C"))
      p ! TestEntity.Get.instance
      val state2 = expectMsgType[TestEntity.State]
      state2.getElements.asScala.toList should ===(List("A", "B", "C"))

      // start another with same persistenceId should recover state
      val p2 = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p2 ! TestEntity.Get.instance
      val state3 = expectMsgType[TestEntity.State]
      state3.getElements.asScala.toList should ===(List("A", "B", "C"))
    }

    "be able to change behavior" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("2"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! TestEntity.Get.instance
      val state = expectMsgType[TestEntity.State]
      state.getMode() should ===(TestEntity.Mode.APPEND)
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("2", "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("2", "B"))
      p ! new TestEntity.ChangeMode(TestEntity.Mode.PREPEND)
      expectMsg(new TestEntity.InPrependMode("2"))
      p ! TestEntity.Add.of("C")
      expectMsg(new TestEntity.Prepended("2", "c"))
      p ! TestEntity.Get.instance
      val state2 = expectMsgType[TestEntity.State]
      state2.getElements.asScala.toList should ===(List("c", "A", "B"))

      // start another with same persistenceId should recover state
      val p2 = system.actorOf(PersistentEntityActor.props("test", Optional.of("2"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p2 ! TestEntity.Get.instance
      val state3 = expectMsgType[TestEntity.State]
      state3.getMode() should ===(TestEntity.Mode.PREPEND)
      state3.getElements.asScala.toList should ===(List("c", "A", "B"))
      p2 ! TestEntity.Add.of("D")
      expectMsg(new TestEntity.Prepended("2", "d"))
      p2 ! TestEntity.Get.instance
      val state4 = expectMsgType[TestEntity.State]
      state4.getElements.asScala.toList should ===(List("d", "c", "A", "B"))

      p2 ! new TestEntity.ChangeMode(TestEntity.Mode.APPEND)
      expectMsg(new TestEntity.InAppendMode("2"))
      p2 ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("2", "E"))
      p2 ! TestEntity.Get.instance
      val state5 = expectMsgType[TestEntity.State]
      state5.getElements.asScala.toList should ===(List("d", "c", "A", "B", "E"))
    }

    "notify when recovery is completed" in {
      val probe = TestProbe()
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("3"),
        () => new TestEntity(system, probe.ref), Optional.empty(), 10.seconds))
      probe.expectMsgType[TestEntity.AfterRecovery]
    }

    "save snapshots" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("4"),
        () => new TestEntity(system), Optional.of(3), 10.seconds))
      for (n <- 1 to 10) {
        p ! TestEntity.Add.of(n.toString)
        expectMsg(new TestEntity.Appended("4", n.toString))
      }

      // start another with same persistenceId should recover state
      // awaitAssert because it is not guaranteed that we will see the snapshot immediately
      within(10.seconds) {
        awaitAssert {

          val probe2 = TestProbe()
          val p2 = system.actorOf(PersistentEntityActor.props("test", Optional.of("4"),
            () => new TestEntity(system, probe2.ref), Optional.of(3), 10.seconds))
          probe2.expectMsgType[TestEntity.Snapshot]
          p2 ! TestEntity.Get.instance
          val state2 = expectMsgType[TestEntity.State]
          state2.getElements.asScala.toList should ===((1 to 10).toList.map(_.toString))
        }
      }
    }

    "persist several events from one command" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("5"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! new TestEntity.Add("a", 3)
      expectMsg(new TestEntity.Appended("5", "A"))
      p ! TestEntity.Get.instance
      val state2 = expectMsgType[TestEntity.State]
      state2.getElements.asScala.toList should ===(List("A", "A", "A"))
    }

    "passivate after idle" in {
      val p = system.actorOf(Props[AbstractPersistentEntityActorSpec.TestPassivationParent])
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("1", "A"))
      val entity = lastSender
      watch(entity)
      expectTerminated(entity)
    }

  }

}
