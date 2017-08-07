/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import java.util.Optional

import akka.persistence.query.Offset
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.ImplicitSender
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.internal.scaladsl.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Mode
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

trait AbstractReadSideSpec[O <: Offset] extends ImplicitSender with ScalaFutures with Eventually { spec: ActorSystemSpec =>

  import system.dispatcher

  // patience config for all async code
  override implicit def patienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(15, Millis)))

  implicit val mat = ActorMaterializer()

  protected val persistentEntityRegistry: PersistentEntityRegistry

  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): Source[EventStreamElement[Event], NotUsed] =
    persistentEntityRegistry.eventStream(aggregateTag, fromOffset)

  def processorFactory(): ReadSideProcessor[TestEntity.Evt]

  def getAppendCount(id: String): Future[Long]

  private val tag = TestEntity.Evt.aggregateEventShards.forEntityId("1")

  private def createTestEntityRef() = {
    system.actorOf(
      PersistentEntityActor.props(
        "test",
        Some("1"),
        () => new TestEntity(system),
        None,
        10.seconds
      )
    )
  }

  private def createReadSideProcessor() = {
    /* read side and injector only needed for deprecated register method */
    val readSide = system.actorOf(
      ReadSideActor.props[TestEntity.Evt](
        processorFactory,
        eventStream,
        classOf[TestEntity.Evt],
        new ClusterStartupTask(testActor),
        20.seconds
      )
    )

    readSide ! EnsureActive(tag.tag)

    expectMsg(Execute)

    processorFactory()
      .buildHandler
      .globalPrepare()
      .foreach { _ =>
        readSide ! Done
      }

    readSide
  }

  private def assertSelectCount(id: String, expected: Long): Unit = {
    within(20.seconds) {
      awaitAssert {
        val count = Await.result(getAppendCount(id), 5.seconds)
        count should ===(expected)
      }
    }
  }

  private def fetchLastOffset(): O =
    processorFactory()
      .buildHandler()
      .prepare(tag)
      .map(_.asInstanceOf[O]) // no ClassTag, no mapTo :-(
      .futureValue

  "ReadSide" must {

    "process events and save query projection" in {

      val p = createTestEntityRef()
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.Add("c")
      expectMsg(TestEntity.Appended("C"))

      val readSide = createReadSideProcessor()

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

      val readSide = createReadSideProcessor()

      val p = createTestEntityRef()
      p ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))

      assertSelectCount("1", 5L)

      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

    "persisted offsets unhandled events" in {

      // count = 5 from previous test steps
      assertSelectCount("1", 5L)
      // this is the last know offset (after processing all 5 events)
      val offsetBefore = fetchLastOffset()

      val readSide = createReadSideProcessor()
      val p = createTestEntityRef()


      p ! TestEntity.ChangeMode(Mode.Prepend)
      expectMsg(TestEntity.InPrependMode)
      p ! TestEntity.Add("f")
      expectMsg(TestEntity.Prepended("f"))

      // count doesn't change because ReadSide only handles Appended events
      // InPrependMode and Prepended events are ignored
      assertSelectCount("1", 5L)

      // however, offset gets updated
      eventually {
        val offsetAfter = fetchLastOffset()
        offsetBefore should not be offsetAfter
      }

      // persisted offset must have change tough
      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

  }

}
