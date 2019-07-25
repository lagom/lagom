/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

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
import akka.actor.ActorLogging
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
import akka.testkit.TestActor.AutoPilot
import akka.testkit.TestActor.KeepRunning
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.RegisterProjectionWorker
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.internal.projection.WorkerHolderActor
import com.lightbend.lagom.projection.Started

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

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

  private def tag(id: String) = TestEntity.Evt.AGGREGATE_EVENT_SHARDS.forEntityId(id)

  private var projectionRegistryProbe: Option[TestProbe] = None

  private def createTestEntityRef(id: String) = {
    system.actorOf(
      PersistentEntityActor.props(
        "test",
        Optional.of(id),
        () => new TestEntity(system),
        Optional.empty(),
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
          processorFactory().buildHandler
            .globalPrepare()
            .toScala
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

    val workerHolderName = WorkerHolderActor.props(
      projectionName,
      processorProps,
      probe.ref
    )

    val readSide: ActorRef = system.actorOf(workerHolderName)

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
      val count = getAppendCount(id).toScala.futureValue
      count shouldBe expected
    }
  }

  private def fetchLastOffset(id: String): Offset =
    processorFactory()
      .buildHandler()
      .prepare(tag(id))
      .toScala
      .mapTo[Offset]
      .futureValue

  "ReadSide" must {

    "register on the projection registry" in {
      withReadSideProcessor("123") { _ =>
        projectionRegistryProbe.get.expectMsgType[ProjectionRegistryActor.RegisterProjectionWorker]
      }
    }

    "process events and save query projection" in {
      val id = "entityId-1"
      val p  = createTestEntityRef(id)

      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended(id, "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended(id, "B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended(id, "C"))

      withReadSideProcessor(id) { _ =>
        assertAppendCount(id, 3L)
        p ! TestEntity.Add.of("d")
        expectMsg(new TestEntity.Appended(id, "D"))
        assertAppendCount(id, 4L)
      }
    }

    "resume from stored offset" in {
      val id = "entityId-2"
      val p  = createTestEntityRef(id)

      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended(id, "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended(id, "B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended(id, "C"))
      p ! TestEntity.Add.of("d")
      expectMsg(new TestEntity.Appended(id, "D"))

      withReadSideProcessor(id) { _ =>
        // count = 4 from previous test step
        assertAppendCount(id, 4L)
      }

      p ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended(id, "E"))

      withReadSideProcessor(id) { _ =>
        assertAppendCount(id, 5L)
      }

    }

    "recover after failure in globalPrepare" in {
      val id = "entityId-recover"

      withReadSideProcessor(id, forcedFailures = 1) { mockRef =>
        // eventually the worker will fail, self-heal and then succeed reporting a failure and a success
        implicit val askTimeout = Timeout(5.seconds)
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

        p ! TestEntity.Add.of("a")
        expectMsg(new TestEntity.Appended(id, "A"))
        p ! TestEntity.Add.of("b")
        expectMsg(new TestEntity.Appended(id, "B"))
        p ! TestEntity.Add.of("c")
        expectMsg(new TestEntity.Appended(id, "C"))

        assertAppendCount(id, 3L)
        val offsetBefore = fetchLastOffset(id)

        p ! new TestEntity.ChangeMode(Mode.PREPEND)
        expectMsg(new TestEntity.InPrependMode(id))
        p ! TestEntity.Add.of("g")
        expectMsg(new TestEntity.Prepended(id, "g"))
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
