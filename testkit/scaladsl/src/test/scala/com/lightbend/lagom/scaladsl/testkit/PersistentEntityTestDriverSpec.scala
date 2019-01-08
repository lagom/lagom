/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import akka.testkit.TestProbe
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.scaladsl.persistence.{ TestEntity, TestEntitySerializerRegistry }
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

class PersistentEntityTestDriverSpec extends ActorSystemSpec(JsonSerializerRegistry.actorSystemSetupFor(TestEntitySerializerRegistry)) {

  def newDriver() = new PersistentEntityTestDriver(system, new TestEntity(system, Some(TestProbe().ref)), "1")

  "PersistentEntityTestDriver" must {
    "produce events and state from commands" in {
      val driver = newDriver()
      val outcome1 = driver.run(TestEntity.Add("a"))
      outcome1.events should ===(List(TestEntity.Appended("A")))
      outcome1.state.elements should ===(List("A"))
      outcome1.issues should be(Nil)

      val outcome2 = driver.run(TestEntity.Add("b"), TestEntity.Add("c"))
      outcome2.events should ===(List(new TestEntity.Appended("B"), new TestEntity.Appended("C")))
      outcome2.state.elements should ===(List("A", "B", "C"))
      outcome2.issues should be(Nil)
    }

    "be able to change behavior" in {
      val driver = newDriver()
      val outcome1 = driver.run(
        TestEntity.Add("a"),
        TestEntity.Add("b"),
        TestEntity.ChangeMode(TestEntity.Mode.Prepend),
        TestEntity.Add("c")
      )
      outcome1.events should ===(List(
        TestEntity.Appended("A"),
        TestEntity.Appended("B"),
        TestEntity.InPrependMode,
        TestEntity.Prepended("c")
      ))
      outcome1.state.elements should ===(List("c", "A", "B"))
      outcome1.issues should be(Nil)
    }

    "produce several events from one command" in {
      val driver = newDriver()
      val outcome1 = driver.run(TestEntity.Add("a", 3))
      outcome1.events should ===(List(
        TestEntity.Appended("A"),
        TestEntity.Appended("A"),
        TestEntity.Appended("A")
      ))
      outcome1.state.elements should ===(List("A", "A", "A"))
      outcome1.issues should be(Nil)
    }

    "record reply side effects" in {
      val driver = newDriver()
      val outcome1 = driver.run(TestEntity.Add("a"), TestEntity.Get)
      val sideEffects = outcome1.sideEffects
      sideEffects(0) should be(PersistentEntityTestDriver.Reply(TestEntity.Appended("A")))
      sideEffects(1) match {
        case PersistentEntityTestDriver.Reply(state: TestEntity.State) =>
        case other => fail("unexpected: " + other)
      }
      outcome1.issues should be(Nil)
    }

    "record unhandled commands" in {
      val driver = newDriver()
      val undefined = TestEntity.UndefinedCmd
      val outcome1 = driver.run(undefined)
      outcome1.issues should be(List(PersistentEntityTestDriver.UnhandledCommand(undefined)))
    }

    "record unhandled events" in {
      val driver = newDriver()
      val unhandledEvent = TestEntity.UnhandledEvtCmd
      val outcome1 = driver.run(unhandledEvent)
      outcome1.issues should be(List(PersistentEntityTestDriver.UnhandledEvent(TestEntity.Unhandled)))
    }

    "be able to handle snapshot state" in {
      val driver = newDriver()
      val outcome1 = driver.initialize(Some(
        TestEntity.State(TestEntity.Mode.Prepend, List("a", "b", "c"))
      ), TestEntity.Prepended("z"))
      outcome1.state.mode should be(TestEntity.Mode.Prepend)
      outcome1.state.elements should ===(List("z", "a", "b", "c"))
      outcome1.events should ===(List(TestEntity.Prepended("z")))
      outcome1.issues should be(Nil)

      val outcome2 = driver.run(TestEntity.Add("y"))
      outcome2.events should ===(List(TestEntity.Prepended("y")))
      outcome2.state.elements should ===(List("y", "z", "a", "b", "c"))
      outcome2.issues should be(Nil)
    }

    "be able to handle null state" in {
      // It's unlikely that Scala developers will use null,
      // but it's consistent with the Java implementation.
      val driver = newDriver()
      val outcomeAfterClearing = driver.run(TestEntity.Clear)

      outcomeAfterClearing.state should be(null)
      outcomeAfterClearing.events.toList should ===(List(TestEntity.Cleared))
      outcomeAfterClearing.issues.toList should be(Nil)
    }
  }

}
