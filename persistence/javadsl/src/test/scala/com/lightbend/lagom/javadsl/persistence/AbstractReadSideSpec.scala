/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.Optional
import java.util.concurrent.CompletionStage

import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import akka.testkit.ImplicitSender
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.javadsl.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.javadsl.persistence.TestEntity.Mode
import com.lightbend.lagom.persistence.ActorSystemSpec
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._

trait AbstractReadSideSpec extends ImplicitSender with ScalaFutures with Eventually with BeforeAndAfter {
  spec: ActorSystemSpec =>

  import system.dispatcher

  // patience config for all async code
  override implicit val patienceConfig = PatienceConfig(20.seconds, 150.millis)

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

  private var readSideActor: Option[ActorRef] = None

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

  class Mock() extends Actor {
    def receive = {
      case Execute =>
        processorFactory()
          .buildHandler
          .globalPrepare()
          .toScala
          .map { _ => Done } pipeTo sender()
    }
  }

  private def createReadSideProcessor() = {
    val mockRef = system.actorOf(Props(new Mock()))
    val processorProps = ReadSideActor.props[TestEntity.Evt](
      ReadSideConfig(),
      classOf[TestEntity.Evt],
      new ClusterStartupTask(mockRef),
      eventStream,
      processorFactory
    )

    val readSide: ActorRef = system.actorOf(processorProps)

    readSide ! EnsureActive(tag.tag)

    readSideActor = Some(readSide)
  }

  after {
    readSideActor.foreach { readSide =>
      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }
  }

  private def assertSelectCount(id: String, expected: Long): Unit = {
    eventually {
      val count = getAppendCount(id).toScala.futureValue
      count shouldBe expected
    }
  }

  private def fetchLastOffset(): Offset =
    processorFactory()
      .buildHandler()
      .prepare(tag)
      .toScala
      .mapTo[Offset]
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

      createReadSideProcessor()

      assertSelectCount("1", 3L)

      p ! TestEntity.Add.of("d")
      expectMsg(new TestEntity.Appended("1", "D"))

      assertSelectCount("1", 4L)

    }

    "resume from stored offset" in {
      // count = 4 from previous test step
      assertSelectCount("1", 4L)

      createReadSideProcessor()

      val p = createTestEntityRef()

      p ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("1", "E"))

      assertSelectCount("1", 5L)

    }

    "persisted offsets for unhandled events" in {

      // count = 5 from previous test steps
      assertSelectCount("1", 5L)
      // this is the last known offset (after processing all 5 events)
      val offsetBefore = fetchLastOffset()

      createReadSideProcessor()
      val p = createTestEntityRef()

      p ! new TestEntity.ChangeMode(Mode.PREPEND)
      expectMsg(new TestEntity.InPrependMode("1"))
      p ! TestEntity.Add.of("f")
      expectMsg(new TestEntity.Prepended("1", "f"))

      // count doesn't change because ReadSide only handles Appended events
      // InPrependMode and Prepended events are ignored
      assertSelectCount("1", 5L)

      // however, persisted offset gets updated
      eventually {
        val offsetAfter = fetchLastOffset()
        offsetBefore should not be offsetAfter
      }
    }
  }

}
