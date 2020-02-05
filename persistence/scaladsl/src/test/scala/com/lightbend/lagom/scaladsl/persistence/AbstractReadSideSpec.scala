/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence

import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.pattern._
import akka.pattern.pipe
import akka.persistence.query.EventEnvelope
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import akka.testkit.ImplicitSender
import akka.testkit.TestProbe
import akka.util.Timeout
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.internal.projection.WorkerConfig
import com.lightbend.lagom.internal.projection.WorkerCoordinator
import com.lightbend.lagom.internal.scaladsl.persistence.PersistentEntityActor
import com.lightbend.lagom.internal.scaladsl.persistence.ReadSideActor
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.projection.Started
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Mode
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

trait AbstractReadSideSpec extends ImplicitSender with ScalaFutures with Eventually with BeforeAndAfter {
  spec: ActorSystemSpec =>

  import system.dispatcher

  // patience config for all async code
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(60.seconds, 150.millis)

  protected val persistentEntityRegistry: PersistentEntityRegistry

  def eventStream[Event <: AggregateEvent[Event]](
      aggregateTag: AggregateEventTag[Event],
      fromOffset: Offset
  ): Source[EventEnvelope, NotUsed] =
    persistentEntityRegistry.eventEnvelopeStream(aggregateTag, fromOffset)

  def processorFactory(): ReadSideProcessor[TestEntity.Evt]

  def getAppendCount(id: String): Future[Long]

  private def tag(id: String) = TestEntity.Evt.aggregateEventShards.forEntityId(id)

  private var projectionRegistryProbe: Option[TestProbe] = None

  private def createTestEntityRef(id: String) = {
    system.actorOf(
      PersistentEntityActor.props(
        "test",
        Some(id),
        () => new TestEntity(system),
        None,
        10.seconds,
        "",
        ""
      )
    )
  }

  class Mock(numberOfFailures: Int = 0) extends Actor with ActorLogging {
    private var stats = Mock.MockStats(0, 0)

    private val prepared = new AtomicBoolean(false)

    def receive: Receive = {
      case Execute =>
        if (numberOfFailures > stats.failureCount) {
          sender() ! Status.Failure(new RuntimeException("Simulated global prepare failure") with NoStackTrace)
          stats = stats.recordFailure()
        } else {
          processorFactory()
            .buildHandler()
            .globalPrepare()
            .map { _ =>
              prepared.set(true)
              Done
            }
            .pipeTo(sender())

          stats = stats.recordSuccess()
        }
      case Mock.GetStats   => sender() ! stats
      case Mock.IsPrepared => sender() ! prepared.get()
    }
  }

  object Mock {
    case class MockStats(successCount: Int, failureCount: Int) {
      def recordFailure() = copy(failureCount = failureCount + 1)
      def recordSuccess() = copy(successCount = successCount + 1)
    }
    case object GetStats
    case object IsPrepared
  }

  private def createReadSideProcessor(entityId: String, clusterStartup: ClusterStartupTask): ActorRef = {
    val projectionName = "abstract-readside-spec-projection"
    val probe          = TestProbe()
    projectionRegistryProbe = Some(probe)

    val processorProps = (coordinates: WorkerCoordinates) =>
      ReadSideActor.props[TestEntity.Evt](
        coordinates.tagName,
        ReadSideConfig(),
        classOf[TestEntity.Evt],
        clusterStartup,
        eventStream,
        () => processorFactory()
      )

    val workerCoordinator = WorkerCoordinator.props(
      projectionName,
      WorkerConfig(system.settings.config),
      processorProps,
      probe.ref
    )

    val readSide: ActorRef = system.actorOf(workerCoordinator)

    // Running a readside is a two step process:
    // 1. sending an EnsureActive so it has the tagName
    // 2. sending the requested state (Started) so it actually starts
    readSide ! EnsureActive(tag(entityId).tag)
    readSide ! Started
    readSide
  }

  private def withReadSideProcessor[T](entityId: String, forcedFailures: Int = 0)(block: ActorRef => T): T = {
    var maybeReadSide: Option[ActorRef] = None
    try {
      val mockRef            = system.actorOf(Props(new Mock(forcedFailures)))
      val readSide: ActorRef = createReadSideProcessor(entityId, new ClusterStartupTask(mockRef))
      maybeReadSide = Some(readSide)

      // block progress until the global prepare of the readside processor has completed
      assertIsPrepared(mockRef)

      block(mockRef)
    } finally {
      stopReadSideActor(maybeReadSide)
    }
  }

  private def stopReadSideActor(actorRef: Option[ActorRef]): Unit = {
    actorRef.foreach { readSide =>
      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }
  }

  private def assertIsPrepared(mockRef: ActorRef) = {
    implicit val timeout: akka.util.Timeout = 10.seconds
    eventually {
      val prepared = (mockRef ? Mock.IsPrepared).mapTo[Boolean].futureValue
      prepared shouldBe true
    }
  }

  private def assertAppendCount(id: String, expected: Long): Unit = {
    eventually {
      val count = getAppendCount(id).futureValue
      count shouldBe expected
    }
  }

  private def fetchLastOffset(id: String): Offset =
    processorFactory()
      .buildHandler()
      .prepare(tag(id))
      .mapTo[Offset]
      .futureValue

  "ReadSide" must {
    "register on the projection registry" in {
      withReadSideProcessor("123") { _ =>
        projectionRegistryProbe.get.expectMsgType[ProjectionRegistryActor.ReportForDuty]
      }
    }

    "process events and save query projection" in {
      val id = "entityId-1"
      val p  = createTestEntityRef(id)
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.Add("c")
      expectMsg(TestEntity.Appended("C"))

      withReadSideProcessor(id) { _ =>
        assertAppendCount(id, 3L)
        p ! TestEntity.Add("d")
        expectMsg(TestEntity.Appended("D"))
        assertAppendCount(id, 4L)
      }
    }

    "resume from stored offset" in {
      val id = "entityId-2"

      val p = createTestEntityRef(id)
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.Add("c")
      expectMsg(TestEntity.Appended("C"))
      p ! TestEntity.Add("d")
      expectMsg(TestEntity.Appended("D"))

      withReadSideProcessor(id) { _ =>
        // count = 4 from previous test step
        assertAppendCount(id, 4L)
      }

      p ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))

      withReadSideProcessor(id) { _ =>
        assertAppendCount(id, 5L)
      }
    }

    "recover after failure in globalPrepare" in {
      val id = "entityId-recover"

      withReadSideProcessor(id, forcedFailures = 1) { mockRef =>
        // eventually the worker will fail, self-heal and then succeed reporting a failure and a success
        implicit val askTimeout: Timeout = Timeout(5.seconds)
        eventually {
          val statsBefore = (mockRef ? Mock.GetStats).mapTo[Mock.MockStats].futureValue
          statsBefore shouldBe Mock.MockStats(successCount = 1, failureCount = 1)
        }
      }
    }

    "persisted offsets for unhandled events" in {
      val id = "unhadled"

      withReadSideProcessor(id) { _ =>
        val p = createTestEntityRef(id)
        p ! TestEntity.Add("a")
        expectMsg(TestEntity.Appended("A"))
        p ! TestEntity.Add("b")
        expectMsg(TestEntity.Appended("B"))
        p ! TestEntity.Add("c")
        expectMsg(TestEntity.Appended("C"))
        assertAppendCount(id, 3L)

        val offsetBefore: Offset = fetchLastOffset(id)

        p ! TestEntity.ChangeMode(Mode.Prepend)
        expectMsg(TestEntity.InPrependMode)
        p ! TestEntity.Add("g")
        expectMsg(TestEntity.Prepended("g"))

        // persisted offset gets updated
        eventually {
          val offsetAfter = fetchLastOffset(id)
          offsetBefore should not be offsetAfter
        }

        // however count doesn't change because ReadSide only handles Appended events
        // InPrependMode and Prepended events are ignored
        assertAppendCount(id, 3L)
      }
    }
  }
}
