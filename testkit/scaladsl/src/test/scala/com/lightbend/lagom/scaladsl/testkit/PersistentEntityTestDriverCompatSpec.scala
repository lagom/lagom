/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import akka.testkit.TestProbe
import com.lightbend.lagom.internal.scaladsl.persistence.PersistentEntityActor
import com.lightbend.lagom.scaladsl.persistence.{ TestEntity, TestEntitySerializerRegistry }
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceSpec

import scala.concurrent.duration._

class PersistentEntityTestDriverCompatSpec extends CassandraPersistenceSpec(TestEntitySerializerRegistry) {

  "PersistentEntityActor and PersistentEntityTestDriver" must {
    "produce same events and state" in {
      val probe1 = TestProbe()
      val p = system.actorOf(PersistentEntityActor.props("test", Some("1"),
        () => new TestEntity(system, Some(probe1.ref)), None, 10.seconds))
      val probe2 = TestProbe()
      val driver = new PersistentEntityTestDriver(system, new TestEntity(system, Some(probe2.ref)), "1")

      val commands = List(
        TestEntity.Get,
        TestEntity.Add("a"),
        TestEntity.Add("b"),
        TestEntity.Add(""),
        TestEntity.ChangeMode(TestEntity.Mode.Prepend),
        TestEntity.Add("C"),
        TestEntity.Add("D", 2),
        TestEntity.Get
      )

      val outcome = driver.run(commands: _*)

      commands.foreach(p ! _)

      val replySideEffects = outcome.sideEffects.collect {
        case PersistentEntityTestDriver.Reply(msg) => msg
      }
      val replies = receiveN(replySideEffects.size)
      replySideEffects should be(replies)
      // Add 2 generates 2 events, but only one reply so drop last event when comparing
      outcome.events.dropRight(1) should be(replies.collect { case evt: TestEntity.Evt => evt })

      outcome.state should be(replies.last)

      expectNoMsg(200.millis)
      probe1.expectMsgType[TestEntity.AfterRecovery]
      probe2.expectMsgType[TestEntity.AfterRecovery]

      outcome.issues should be(Nil)
    }

  }

}
