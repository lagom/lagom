/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.Optional
import java.util.concurrent.CompletionStage

import akka.actor.ActorRef
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import akka.testkit.ImplicitSender
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.javadsl.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.javadsl.persistence.Offset.{ Sequence, TimeBasedUUID }
import com.lightbend.lagom.javadsl.persistence.TestEntity.Mode
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.spi.persistence.OffsetStore
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }

import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

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
  ): Source[akka.japi.Pair[Event, Offset], NotUsed] =
    persistentEntityRegistry.eventStream(aggregateTag, fromOffset)

  def processorFactory(): ReadSideProcessor[TestEntity.Evt]

  def getAppendCount(id: String): CompletionStage[java.lang.Long]

  private val tag = TestEntity.Evt.AGGREGATE_EVENT_SHARDS.forEntityId("1")

  private def createTestEntityRef() = {
    system.actorOf(
      PersistentEntityActor.props(
        "test",
        Optional.of("1"),
        () => new TestEntity(system),
        Optional.empty(),
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
      .buildHandler()
      .globalPrepare().toScala
      .foreach { _ =>
        readSide ! Done
      }

    readSide
  }

  private def assertSelectCount(id: String, expected: Long): Unit = {
    eventually {
      val count = getAppendCount(id).toScala.futureValue
      count shouldBe expected
    }
  }

  private def fetchLastOffset(): O =
    processorFactory()
      .buildHandler()
      .prepare(tag)
      .toScala
      .map(_.asInstanceOf[O]) // no ClassTag, no mapTo :-(
      .futureValue

  "ReadSide" must {

    "process events and save query projection" in {

      val p = createTestEntityRef()

      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("1", "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("1", "B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended("1", "C"))

      val readSide = createReadSideProcessor()

      assertSelectCount("1", 3L)

      p ! TestEntity.Add.of("d")
      expectMsg(new TestEntity.Appended("1", "D"))

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

      p ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("1", "E"))

      assertSelectCount("1", 5L)

      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

    "persisted offsets unhandled events" in {

      // count = 5 from previous test steps
      assertSelectCount("1", 5L)

      val readSide = createReadSideProcessor()

      val p = createTestEntityRef()

      val offsetBefore = fetchLastOffset()

      p ! new TestEntity.ChangeMode(Mode.PREPEND)
      expectMsg(new TestEntity.InPrependMode("1"))
      p ! TestEntity.Add.of("f")
      expectMsg(new TestEntity.Prepended("1", "f"))

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
