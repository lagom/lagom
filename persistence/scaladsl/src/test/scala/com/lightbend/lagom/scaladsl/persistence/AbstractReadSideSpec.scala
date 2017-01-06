/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import java.util.Optional

import akka.persistence.query.Offset
import akka.{ Done, NotUsed }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.ImplicitSender

import scala.concurrent.duration._
import com.lightbend.lagom.internal.scaladsl.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute

import scala.concurrent.Await
import com.lightbend.lagom.persistence.ActorSystemSpec

import scala.concurrent.Future

trait AbstractReadSideSpec extends ImplicitSender { spec: ActorSystemSpec =>
  import system.dispatcher

  implicit val mat = ActorMaterializer()

  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): Source[EventStreamElement[Event], NotUsed]

  def processorFactory(): ReadSideProcessor[TestEntity.Evt]

  def getAppendCount(id: String): Future[Long]

  private def assertSelectCount(id: String, expected: Long): Unit = {
    within(20.seconds) {
      awaitAssert {
        val count = Await.result(getAppendCount(id), 5.seconds)
        count should ===(expected)
      }
    }
  }

  private def createReadSideProcessor(tag: AggregateEventTag[TestEntity.Evt]) = {
    /* read side and injector only needed for deprecated register method */
    val readSide = system.actorOf(ReadSideActor.props[TestEntity.Evt](
      processorFactory,
      eventStream, classOf[TestEntity.Evt], new ClusterStartupTask(testActor), 20.seconds
    ))

    readSide ! EnsureActive(tag.tag)

    expectMsg(Execute)

    processorFactory().buildHandler.globalPrepare().foreach { _ =>
      readSide ! Done
    }

    readSide
  }

  "ReadSide" must {

    "process events and save query projection" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Some("1"),
        () => new TestEntity(system), None, 10.seconds))
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.Add("c")
      expectMsg(TestEntity.Appended("C"))

      val readSide = createReadSideProcessor(TestEntity.Evt.aggregateEventShards.forEntityId("1"))

      assertSelectCount("1", 3L)

      p ! TestEntity.Add("d")
      expectMsg(TestEntity.Appended("D"))

      assertSelectCount("1", 4L)

      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

    "resume from stored offset" in {
      // count = 4 from previous test step
      assertSelectCount("1", 4L)

      val readSide = createReadSideProcessor(TestEntity.Evt.aggregateEventShards.forEntityId("1"))

      val p = system.actorOf(PersistentEntityActor.props("test", Some("1"),
        () => new TestEntity(system), None, 10.seconds))
      p ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))

      assertSelectCount("1", 5L)

      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

  }

}
