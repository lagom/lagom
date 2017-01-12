/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.Optional
import java.util.concurrent.CompletionStage

import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import akka.testkit.ImplicitSender
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.javadsl.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.persistence.ActorSystemSpec

import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

trait AbstractReadSideSpec extends ImplicitSender { spec: ActorSystemSpec =>
  import system.dispatcher

  implicit val mat = ActorMaterializer()

  protected val persistentEntityRegistry: PersistentEntityRegistry

  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): Source[akka.japi.Pair[Event, Offset], NotUsed] =
    persistentEntityRegistry.eventStream(aggregateTag, fromOffset)

  def processorFactory(): ReadSideProcessor[TestEntity.Evt]

  def getAppendCount(id: String): CompletionStage[java.lang.Long]

  private def assertSelectCount(id: String, expected: Long): Unit = {
    within(20.seconds) {
      awaitAssert {
        val count = Await.result(getAppendCount(id).toScala, 5.seconds)
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

    processorFactory().buildHandler().globalPrepare().toScala.foreach { _ =>
      readSide ! Done
    }

    readSide
  }

  "ReadSide" must {

    "process events and save query projection" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("1", "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("1", "B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended("1", "C"))

      val readSide = createReadSideProcessor(TestEntity.Evt.AGGREGATE_EVENT_SHARDS.forEntityId("1"))

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

      val readSide = createReadSideProcessor(TestEntity.Evt.AGGREGATE_EVENT_SHARDS.forEntityId("1"))

      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("1", "E"))

      assertSelectCount("1", 5L)

      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

  }

}
