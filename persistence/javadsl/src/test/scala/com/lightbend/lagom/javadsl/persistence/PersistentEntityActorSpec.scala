/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import java.util.Optional

import akka.testkit.TestProbe
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion
import com.lightbend.lagom.internal.persistence.PersistentEntityActor
import com.lightbend.lagom.persistence.PersistenceSpec

object PersistentEntityActorSpec {

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

class PersistentEntityActorSpec extends PersistenceSpec {

  protected def newActor(actorSystem: ActorSystem, entityId: Optional[String], snapshotAfter: Optional[Int] = Optional.empty(), probe: Optional[ActorRef] = Optional.empty()): ActorRef =
    if (probe.isPresent) {
      system.actorOf(PersistentEntityActor.props("test", entityId,
        () => new TestEntity(system, probe.get()), snapshotAfter, 10.seconds))
    }
    else {
      system.actorOf(PersistentEntityActor.props("test", entityId,
        () => new TestEntity(system), snapshotAfter, 10.seconds))
    }

  "PersistentEntityActor" must {
    "persist events" in {
      val p = newActor(system, Optional.of("1"))
      p ! TestEntity.Get.instance
      val state = expectMsgType[TestEntity.State]
      state.getElements.size should ===(0)
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended("C"))
      p ! TestEntity.Get.instance
      val state2 = expectMsgType[TestEntity.State]
      state2.getElements.asScala.toList should ===(List("A", "B", "C"))

      // start another with same persistenceId should recover state
      val p2 = newActor(system, Optional.of("1"))
      p2 ! TestEntity.Get.instance
      val state3 = expectMsgType[TestEntity.State]
      state3.getElements.asScala.toList should ===(List("A", "B", "C"))
    }

    "be able to change behavior" in {
      val p = newActor(system, Optional.of("2"))
      p ! TestEntity.Get.instance
      val state = expectMsgType[TestEntity.State]
      state.getMode() should ===(TestEntity.Mode.APPEND)
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("B"))
      p ! new TestEntity.ChangeMode(TestEntity.Mode.PREPEND)
      expectMsgType[TestEntity.InPrependMode]
      p ! TestEntity.Add.of("C")
      expectMsg(new TestEntity.Prepended("c"))
      p ! TestEntity.Get.instance
      val state2 = expectMsgType[TestEntity.State]
      state2.getElements.asScala.toList should ===(List("c", "A", "B"))

      // start another with same persistenceId should recover state
      val p2 = newActor(system, Optional.of("2"))
      p2 ! TestEntity.Get.instance
      val state3 = expectMsgType[TestEntity.State]
      state3.getMode() should ===(TestEntity.Mode.PREPEND)
      state3.getElements.asScala.toList should ===(List("c", "A", "B"))
      p2 ! TestEntity.Add.of("D")
      expectMsg(new TestEntity.Prepended("d"))
      p2 ! TestEntity.Get.instance
      val state4 = expectMsgType[TestEntity.State]
      state4.getElements.asScala.toList should ===(List("d", "c", "A", "B"))

      p2 ! new TestEntity.ChangeMode(TestEntity.Mode.APPEND)
      expectMsgType[TestEntity.InAppendMode]
      p2 ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("E"))
      p2 ! TestEntity.Get.instance
      val state5 = expectMsgType[TestEntity.State]
      state5.getElements.asScala.toList should ===(List("d", "c", "A", "B", "E"))
    }

    "notify when recovery is completed" in {
      val probe = TestProbe()
      val p = newActor(system, Optional.of("3"), Optional.empty(), Optional.of(probe.ref))
      probe.expectMsgType[TestEntity.AfterRecovery]
    }

    "save snapshots" in {
      val p = newActor(system, Optional.of("4"), Optional.of(30))
      for (n <- 1 to 10) {
        p ! TestEntity.Add.of(n.toString)
        expectMsg(new TestEntity.Appended(n.toString))
      }

      // start another with same persistenceId should recover state
      // awaitAssert because it is not guaranteed that we will see the snapshot immediately
      within(10.seconds) {
        awaitAssert {
          val probe2 = TestProbe()
          val p2 = newActor(system, Optional.of("4"), Optional.of(3), Optional.of(probe2.ref))
          probe2.expectMsgType[TestEntity.Snapshot]
          p2 ! TestEntity.Get.instance
          val state2 = expectMsgType[TestEntity.State]
          state2.getElements.asScala.toList should ===((1 to 10).toList.map(_.toString))
        }
      }
    }

    "persist several events from one command" in {
      val p = newActor(system, Optional.of("5"))
      p ! new TestEntity.Add("a", 3)
      expectMsg(new TestEntity.Appended("A"))
      p ! TestEntity.Get.instance
      val state2 = expectMsgType[TestEntity.State]
      state2.getElements.asScala.toList should ===(List("A", "A", "A"))
    }

    //    "passivate after idle" in {
    //      val p = system.actorOf(Props[PersistentEntityActorSpec.TestPassivationParent])
    //      p ! TestEntity.Add.of("a")
    //      expectMsg(new TestEntity.Appended("A"))
    //      val entity = lastSender
    //      watch(entity)
    //      expectTerminated(entity)
    //    }
  }

}

