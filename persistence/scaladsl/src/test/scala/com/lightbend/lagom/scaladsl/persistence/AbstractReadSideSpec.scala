/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import akka.persistence.query.Offset
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.ImplicitSender
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.internal.scaladsl.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Mode
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

import scala.concurrent.Future
import scala.concurrent.duration._

trait AbstractReadSideSpec extends ImplicitSender with ScalaFutures with Eventually with BeforeAndAfter {
  spec: ActorSystemSpec =>

  import system.dispatcher

  // patience config for all async code
  override implicit val patienceConfig = PatienceConfig(8.seconds, 150.millis)

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

  private var readSideActor: Option[ActorRef] = None

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

  class Mock() extends Actor {
    def receive = {
      case Execute =>
        processorFactory()
          .buildHandler
          .globalPrepare()
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
      val count = getAppendCount(id).futureValue
      count shouldBe expected
    }
  }

  private def fetchLastOffset(): Offset =
    processorFactory()
      .buildHandler()
      .prepare(tag)
      .mapTo[Offset]
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

      createReadSideProcessor()

      assertSelectCount("1", 3L)

      p ! TestEntity.Add("d")
      expectMsg(TestEntity.Appended("D"))

      assertSelectCount("1", 4L)

    }

    "resume from stored offset" in {
      // count = 4 from previous test step
      assertSelectCount("1", 4L)

      createReadSideProcessor()

      val p = createTestEntityRef()
      p ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))

      assertSelectCount("1", 5L)
    }

    "persisted offsets for unhandled events" in {

      createReadSideProcessor()

      // count = 5 from previous test steps
      assertSelectCount("1", 5L)
      // this is the last known offset (after processing all 5 events)
      val offsetBefore = fetchLastOffset()

      val p = createTestEntityRef()

      p ! TestEntity.ChangeMode(Mode.Prepend)
      expectMsg(TestEntity.InPrependMode)
      p ! TestEntity.Add("f")
      expectMsg(TestEntity.Prepended("f"))

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
