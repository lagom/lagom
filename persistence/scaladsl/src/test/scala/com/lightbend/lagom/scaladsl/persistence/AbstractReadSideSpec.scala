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
import akka.actor.ActorLogging
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
import akka.testkit.TestActor.AutoPilot
import akka.testkit.TestActor.KeepRunning
import akka.testkit.TestProbe
import akka.util.Timeout
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.RegisterProjectionWorker
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.internal.projection.WorkerHolderActor
import com.lightbend.lagom.projection.Started

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

trait AbstractReadSideSpec extends ImplicitSender with ScalaFutures with Eventually with BeforeAndAfter {
  spec: ActorSystemSpec =>

  import system.dispatcher

  // patience config for all async code
  implicit override val patienceConfig = PatienceConfig(20.seconds, 150.millis)

  implicit val mat = ActorMaterializer()

  protected val persistentEntityRegistry: PersistentEntityRegistry

  def eventStream[Event <: AggregateEvent[Event]](
      aggregateTag: AggregateEventTag[Event],
      fromOffset: Offset
  ): Source[EventStreamElement[Event], NotUsed] =
    persistentEntityRegistry.eventStream(aggregateTag, fromOffset)

  def processorFactory(): ReadSideProcessor[TestEntity.Evt]

  def getAppendCount(id: String): Future[Long]

  private def tag(id: String) = TestEntity.Evt.aggregateEventShards.forEntityId(id)

  private var readSideActor: Option[ActorRef]            = None
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

    def receive = getStats.orElse {
      case Execute =>
        log.warning(s"""
                       |
                       |   Preparing readSide: $numberOfFailures > ${stats.failureCount}
                       |
           """.stripMargin)
        if (numberOfFailures > stats.failureCount) {
          sender() ! Status.Failure(new RuntimeException("Simulated global prepare failure") with NoStackTrace)
          stats = stats.recordFailure()
        } else {
          processorFactory().buildHandler
            .globalPrepare()
            .map { _ =>
              Done
            }
            .pipeTo(sender())

          stats = stats.recordSuccess()
        }
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
    case object GetStats
  }

  private def createReadSideProcessor(
      entityId: String,
      forcedFailures: Int = 0
  ) = {

    val projectionName = "abstract-readside-spec-projection"
    val probe          = TestProbe()
    projectionRegistryProbe = Some(probe)

    val mockRef = system.actorOf(Props(new Mock(forcedFailures)))
    val processorProps = (coordinates: WorkerCoordinates) =>
      ReadSideActor.props[TestEntity.Evt](
        coordinates.tagName,
        ReadSideConfig(),
        classOf[TestEntity.Evt],
        new ClusterStartupTask(mockRef),
        eventStream,
        () => processorFactory()
      )

    val workerHolderName = WorkerHolderActor.props(
      projectionName,
      processorProps,
      probe.ref
    )

    val readSide: ActorRef = system.actorOf(workerHolderName)

    // Triggering a readside is a three step process:
    // 1. send an Ensure Active
    readSide ! EnsureActive(tag(entityId).tag)
    // 2. expect a registration request back
    probe.setAutoPilot(new AutoPilot {
      def run(sender: ActorRef, msg: Any): AutoPilot =
        msg match {
          // 3. reply the registration request with a `requestedStatus` (defaults to `Started`)
          case RegisterProjectionWorker(_) =>
            sender ! Started
            KeepRunning
          case _ => KeepRunning

        }
    })

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

  private def fetchLastOffset(id: String): Offset =
    processorFactory()
      .buildHandler()
      .prepare(tag(id))
      .mapTo[Offset]
      .futureValue

  "ReadSide" must {

    "register on the projection registry" in {
      createReadSideProcessor("123")
      projectionRegistryProbe.get.expectMsgType[ProjectionRegistryActor.RegisterProjectionWorker]
    }

    "process events and save query projection" in {
      val id = "1"
      val p  = createTestEntityRef(id)
      p ! TestEntity.Add("a")
      expectMsg(TestEntity.Appended("A"))
      p ! TestEntity.Add("b")
      expectMsg(TestEntity.Appended("B"))
      p ! TestEntity.Add("c")
      expectMsg(TestEntity.Appended("C"))

      createReadSideProcessor(id)

      assertAppendCount(id, 3L)

      p ! TestEntity.Add("d")
      expectMsg(TestEntity.Appended("D"))

      assertAppendCount(id, 4L)

    }

    "resume from stored offset" in {
      val id = "1"
      // count = 4 from previous test step
      assertAppendCount(id, 4L)

      createReadSideProcessor(id)

      val p = createTestEntityRef(id)
      p ! TestEntity.Add("e")
      expectMsg(TestEntity.Appended("E"))

      assertAppendCount(id, 5L)
    }

    "recover after failure in globalPrepare" in {
      val id = "recover"
      // countBefore is probably 5 from previous test steps, but we don't care about the exact value.
      val countBefore: Long = getAppendCount(id).futureValue

      val mockRef = createReadSideProcessor(id, forcedFailures = 1)

      val p = createTestEntityRef(id)
      p ! TestEntity.Add("f")
      expectMsg(TestEntity.Appended("F"))

      // eventually the worker will fail, self-heal and then succeed reporting a failure and a success
      implicit val askTimeout = Timeout(5.seconds)
      eventually {
        val statsBefore = (mockRef ? Mock.GetStats).mapTo[Mock.MockStats].futureValue
        statsBefore.failureCount shouldBe 1
        statsBefore.successCount shouldBe 1
      }

      // offset must progress once read-side processor is recovered
      assertAppendCount(id, countBefore + 1)
    }

    "persisted offsets for unhandled events" in {

      val id = "unhadled"
      // countBefore is probably 6 from previous test steps, but we don't care about the exact value.
      val countBefore: Long    = getAppendCount(id).futureValue
      val offsetBefore: Offset = fetchLastOffset(id)

      createReadSideProcessor(id)
      val p = createTestEntityRef(id)

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
      assertAppendCount(id, countBefore)

    }
  }
}
