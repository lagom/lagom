/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import java.util.Optional

import scala.collection.JavaConverters._
import akka.testkit.TestProbe
import com.google.common.collect.ImmutableList
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.javadsl.persistence.TestEntity

import scala.annotation.varargs

class PersistentEntityTestDriverSpec extends ActorSystemSpec {

  def newDriver() = new PersistentEntityTestDriver(system, new TestEntity(system, TestProbe().ref), "1")

  "PersistentEntityTestDriver" must {
    "produce events and state from commands" in {
      val driver = newDriver()
      val outcome1 = driver.run(TestEntity.Add.of("a"))
      outcome1.events.asScala.toList should ===(List(new TestEntity.Appended("1", "A")))
      outcome1.state.getElements.asScala.toList should ===(List("A"))
      outcome1.issues.asScala.toList should be(Nil)

      val outcome2 = driver.run(TestEntity.Add.of("b"), TestEntity.Add.of("c"))
      outcome2.events.asScala.toList should ===(List(new TestEntity.Appended("1", "B"), new TestEntity.Appended("1", "C")))
      outcome2.state.getElements.asScala.toList should ===(List("A", "B", "C"))
      outcome2.issues.asScala.toList should be(Nil)
    }

    "be able to change behavior" in {
      val driver = newDriver()
      val outcome1 = driver.run(
        TestEntity.Add.of("a"),
        TestEntity.Add.of("b"),
        new TestEntity.ChangeMode(TestEntity.Mode.PREPEND),
        TestEntity.Add.of("c")
      )
      outcome1.events.asScala.toList should ===(List(
        new TestEntity.Appended("1", "A"),
        new TestEntity.Appended("1", "B"),
        new TestEntity.InPrependMode("1"),
        new TestEntity.Prepended("1", "c")
      ))
      outcome1.state.getElements.asScala.toList should ===(List("c", "A", "B"))
      outcome1.issues.asScala.toList should be(Nil)
    }

    "produce several events from one command" in {
      val driver = newDriver()
      val outcome1 = driver.run(new TestEntity.Add("a", 3))
      outcome1.events.asScala.toList should ===(List(
        new TestEntity.Appended("1", "A"),
        new TestEntity.Appended("1", "A"),
        new TestEntity.Appended("1", "A")
      ))
      outcome1.state.getElements.asScala.toList should ===(List("A", "A", "A"))
      outcome1.issues.asScala.toList should be(Nil)
    }

    "record reply side effects" in {
      val driver = newDriver()
      val outcome1 = driver.run(TestEntity.Add.of("a"), TestEntity.Get.instance)
      val sideEffects = outcome1.sideEffects.asScala.toVector
      sideEffects(0) should be(new PersistentEntityTestDriver.Reply(new TestEntity.Appended("1", "A")))
      sideEffects(1) match {
        case PersistentEntityTestDriver.Reply(state: TestEntity.State) =>
        case other => fail("unexpected: " + other)
      }
      outcome1.issues.asScala.toList should be(Nil)
    }

    "record unhandled commands" in {
      val driver = newDriver()
      val undefined = new TestEntity.UndefinedCmd
      val outcome1 = driver.run(undefined)
      outcome1.issues.asScala.toList should be(List(PersistentEntityTestDriver.UnhandledCommand(undefined)))
    }

    "be able to handle snapshot state" in {
      val driver = newDriver()
      val outcome1 = driver.initialize(Optional.of(
        new TestEntity.State(TestEntity.Mode.PREPEND, ImmutableList.of("a", "b", "c"))
      ), new TestEntity.Prepended("1", "z"))
      outcome1.state.getMode should be(TestEntity.Mode.PREPEND)
      outcome1.state.getElements.asScala.toList should ===(List("z", "a", "b", "c"))
      outcome1.events.asScala.toList should ===(List(new TestEntity.Prepended("1", "z")))
      outcome1.issues.asScala.toList should be(Nil)

      val outcome2 = driver.run(TestEntity.Add.of("y"))
      outcome2.events.asScala.toList should ===(List(new TestEntity.Prepended("1", "y")))
      outcome2.state.getElements.asScala.toList should ===(List("y", "z", "a", "b", "c"))
      outcome2.issues.asScala.toList should be(Nil)
    }

  }

}
