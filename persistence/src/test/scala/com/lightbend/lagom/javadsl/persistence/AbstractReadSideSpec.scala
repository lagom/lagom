/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.Optional

import akka.{ Done, NotUsed }
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import akka.testkit.ImplicitSender

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.internal.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute
import com.lightbend.lagom.javadsl.persistence.TestEntity.InPrependMode

trait AbstractReadSideSpec extends ImplicitSender { spec: ActorSystemSpec =>
  import system.dispatcher

  implicit val mat = ActorMaterializer()

  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): Source[akka.japi.Pair[Event, Offset], NotUsed]

  def processorFactory(): ReadSideProcessor[TestEntity.Evt]

  def assertSelectCount(id: String, expected: Long): Unit

  final def createReadSideProcessor(tag: AggregateEventTag[TestEntity.Evt]) = {
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

    "use correct tag" in {
      val shardNo = AggregateEventTag.selectShard(TestEntity.Evt.NUM_SHARDS, "1")
      new TestEntity.Appended("1", "A").aggregateTag.tag should ===(classOf[TestEntity.Evt].getName + shardNo)
      new InPrependMode("1").aggregateTag.tag should ===(classOf[TestEntity.Evt].getName + shardNo)
    }

    "process events and save query projection" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("1", "A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("1", "B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended("1", "C"))

      val readSide = createReadSideProcessor(new TestEntity.Appended("1", "").aggregateTag())

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

      val readSide = createReadSideProcessor(new TestEntity.Appended("1", "").aggregateTag())

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
