/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence

import java.util.Optional
import java.util.concurrent.CompletionStage

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import akka.testkit.ImplicitSender
import akka.util.Timeout
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.internal.javadsl.persistence.PersistentEntityActor
import com.lightbend.lagom.internal.javadsl.persistence.ReadSideActor
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.javadsl.persistence.TestEntity.Mode
import com.lightbend.lagom.persistence.ActorSystemSpec
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import akka.pattern._
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor

import scala.compat.java8.FutureConverters._
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
        processorFactory().buildHandler
          .globalPrepare()
          .toScala
          .map { _ =>
            Done
          }
          .pipeTo(sender())

        stats = stats.recordSuccess()
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

    "register on the projection registry" in {
      val testProbe = TestProbe()
      createReadSideProcessor(projectionRegistryProbe = testProbe)

      testProbe.expectMsgType[ProjectionRegistryActor.RegisterProjection]
    }

    "process events and save query projection" in {

      val p = createTestEntityRef()

      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("1", "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("1", "B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended("1", "C"))

      createReadSideProcessor()

      assertAppendCount("1", 3L)

      p ! TestEntity.Add.of("d")
      expectMsg(new TestEntity.Appended("1", "D"))

      assertAppendCount("1", 4L)

    }

    "resume from stored offset" in {
      // count = 4 from previous test step
      assertAppendCount("1", 4L)

      createReadSideProcessor()

      val p = createTestEntityRef()

      p ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("1", "E"))

      assertAppendCount("1", 5L)

    }

    "recover after failure in globalPrepare" in {

      val mockRef = createReadSideProcessor(inFailureMode = true)

      val p = createTestEntityRef()
      p ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("1", "E"))

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

      // count = 5 from previous test steps
      assertAppendCount("1", 6L)
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
      assertAppendCount("1", 6L)

      // however, persisted offset gets updated
      eventually {
        val offsetAfter = fetchLastOffset()
        offsetBefore should not be offsetAfter
      }
    }
  }

}
