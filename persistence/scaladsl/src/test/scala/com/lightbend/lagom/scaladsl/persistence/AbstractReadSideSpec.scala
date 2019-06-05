/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.pattern.pipe
import akka.persistence.query.Offset
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.ImplicitSender
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.internal.scaladsl.persistence.PersistentEntityActor
import com.lightbend.lagom.internal.scaladsl.persistence.ReadSideActor
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Mode
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import akka.pattern._
import akka.testkit.TestProbe
import akka.util.Timeout
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor

import scala.concurrent.Future
import scala.concurrent.duration._

trait AbstractReadSideSpec extends ImplicitSender with ScalaFutures with Eventually with BeforeAndAfter {
  spec: ActorSystemSpec =>

  import system.dispatcher

  // patience config for all async code
  implicit override val patienceConfig = PatienceConfig(60.seconds, 150.millis)

  implicit val mat = ActorMaterializer()

  protected val persistentEntityRegistry: PersistentEntityRegistry

  def eventStream[Event <: AggregateEvent[Event]](
      aggregateTag: AggregateEventTag[Event],
      fromOffset: Offset
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
        10.seconds,
        "",
        ""
      )
    )
  }

  class Mock(inFailureMode: Boolean = false) extends Actor {

    private var stats = Mock.MockStats(0, 0)

    def receive = if (inFailureMode) failureMode else successMode

    def successMode: Receive = getStats.orElse {
      case Execute =>
        processorFactory()
          .buildHandler()
          .globalPrepare()
          .map { _ =>
            Done
          }
          .pipeTo(sender())
        stats = stats.recordSuccess()

      case Mock.BecomeSuccessful => context.become(failureMode)
    }

    def failureMode: Receive = getStats.orElse {
      case Execute =>
        sender() ! Status.Failure(new RuntimeException("Simulated global prepare failure"))
        stats = stats.recordFailure()

      case Mock.BecomeSuccessful => context.become(successMode)
    }

    def getStats: Receive = {
      case Mock.GetStats => sender() ! stats
    }

  }

  object Mock {
    case class MockStats(successCount: Int, failureCount: Int) {
      def recordFailure() = copy(failureCount = failureCount + 1)
      def recordSuccess() = copy(successCount = successCount + 1)
    }
    case object BecomeSuccessful
    case object GetStats
  }

  private def createReadSideProcessor(
      projectionRegistryProbe: TestProbe = TestProbe(),
      inFailureMode: Boolean = false
  ) = {
    val mockRef = system.actorOf(Props(new Mock(inFailureMode)))
    val processorProps = ReadSideActor.props[TestEntity.Evt](
      "abstract-readside-spec-stream",
      "abstract-readside-spec-projection",
      ReadSideConfig(),
      classOf[TestEntity.Evt],
      new ClusterStartupTask(mockRef),
      eventStream,
      () => processorFactory(),
      projectionRegistryProbe.ref
    )

    val readSide: ActorRef = system.actorOf(processorProps)

    readSide ! EnsureActive(tag.tag)

    readSideActor = Some(readSide)
    mockRef
  }

  after {
    readSideActor.foreach { readSide =>
      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }
  }

  private def assertAppendCount(id: String, expected: Long): Unit = {
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

    "register on the projection registry" in {
      val testProbe = TestProbe()
      createReadSideProcessor(projectionRegistryProbe = testProbe)

      testProbe.expectMsgType[ProjectionRegistryActor.RegisterProjection]
    }

    "process events and save query projection" in {

      val p = createTestEntityRef()
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.Add("c")
      expectMsg(TestEntity.Appended("C"))

      createReadSideProcessor()

      assertAppendCount("1", 3L)

      p ! TestEntity.Add("d")
      expectMsg(TestEntity.Appended("D"))

      assertAppendCount("1", 4L)

    }

    "resume from stored offset" in {
      // count = 4 from previous test step
      assertAppendCount("1", 4L)

      createReadSideProcessor()

      val p = createTestEntityRef()
      p ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))

      assertAppendCount("1", 5L)
    }

    "recover after failure in globalPrepare" in {

      val mockRef = createReadSideProcessor(inFailureMode = true)

      val p = createTestEntityRef()
      p ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))

      implicit val askTimeout = Timeout(5.seconds)
      eventually {
        val statsBefore = (mockRef ? Mock.GetStats).mapTo[Mock.MockStats].futureValue
        statsBefore.failureCount shouldBe 1
        statsBefore.successCount shouldBe 0
      }

      // count = 5 from previous test steps
      assertAppendCount("1", 5L)

      // switch mock to 'Success' mode
      mockRef ! Mock.BecomeSuccessful
      readSideActor.foreach(_ ! EnsureActive(tag.tag))

      eventually {
        val statsAfter = (mockRef ? Mock.GetStats).mapTo[Mock.MockStats].futureValue
        statsAfter.successCount shouldBe 1
      }

      // offset must progress once read-side processor is recovered
      assertAppendCount("1", 6L)
    }

    "persisted offsets for unhandled events" in {

      createReadSideProcessor()

      // count = 5 from previous test steps
      assertAppendCount("1", 6L)
      // this is the last known offset (after processing all 5 events)
      val offsetBefore = fetchLastOffset()

      val p = createTestEntityRef()

      p ! TestEntity.ChangeMode(Mode.Prepend)
      expectMsg(TestEntity.InPrependMode)
      p ! TestEntity.Add("f")
      expectMsg(TestEntity.Prepended("f"))

      // count doesn't change because ReadSide only handles Appended events
      // InPrependMode and Prepended events are ignored
      assertAppendCount("1", 6L)

      // however, persisted offset gets updated
      eventually {
        val offsetAfter = fetchLastOffset()
        offsetBefore should not be offsetAfter
      }
    }
  }
}
