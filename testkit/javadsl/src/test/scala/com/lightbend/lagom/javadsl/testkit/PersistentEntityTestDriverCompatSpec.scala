/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import com.lightbend.lagom.javadsl.persistence.TestEntity
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.javadsl.persistence.PersistentEntityActor
import java.util.Optional

import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraPersistenceSpec

class PersistentEntityTestDriverCompatSpec extends CassandraPersistenceSpec {

  "PersistentEntityActor and PersistentEntityTestDriver" must {
    "produce same events and state" in {
      val probe1 = TestProbe()
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system, probe1.ref), Optional.empty(), 10.seconds))
      val probe2 = TestProbe()
      val driver = new PersistentEntityTestDriver(system, new TestEntity(system, probe2.ref), "1")

      val commands = List(
        TestEntity.Get.instance,
        TestEntity.Add.of("a"),
        TestEntity.Add.of("b"),
        TestEntity.Add.of(""),
        new TestEntity.ChangeMode(TestEntity.Mode.PREPEND),
        TestEntity.Add.of("C"),
        new TestEntity.Add("D", 2),
        TestEntity.Get.instance
      )

      val outcome = driver.run(commands: _*)

      commands.foreach(p ! _)

      val replySideEffects = outcome.sideEffects.asScala.collect {
        case PersistentEntityTestDriver.Reply(msg) => msg
      }
      val replies = receiveN(replySideEffects.size)
      replySideEffects should be(replies)
      // Add 2 generates 2 events, but only one reply so drop last event when comparing
      outcome.events.asScala.dropRight(1) should be(replies.collect { case evt: TestEntity.Evt => evt })

      outcome.state should be(replies.last)

      expectNoMsg(200.millis)
      probe1.expectMsgType[TestEntity.AfterRecovery]
      probe2.expectMsgType[TestEntity.AfterRecovery]

      outcome.issues.asScala.toList should be(Nil)
    }

  }

}
