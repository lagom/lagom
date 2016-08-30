/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.Optional

import akka.{ Done, NotUsed }
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import com.lightbend.lagom.internal.persistence.GlobalPrepareReadSideActor.Prepare

import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.internal.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraReadSideImpl, CassandraSessionImpl }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.javadsl.persistence.Offset.TimeBasedUUID
import com.lightbend.lagom.javadsl.persistence.TestEntity.InPrependMode

object CassandraReadSideSpec {

  val config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    """)

}

class CassandraReadSideSpec extends PersistenceSpec(CassandraReadSideSpec.config) {
  import CassandraReadSideSpec._
  import system.dispatcher

  implicit val mat = ActorMaterializer()
  lazy val testSession: CassandraSession = new CassandraSessionImpl(system)
  lazy val queries = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): Source[akka.japi.Pair[Event, Offset], NotUsed] = {
    val tag = aggregateTag.tag
    val offset = fromOffset match {
      case Offset.NONE         => queries.firstOffset
      case uuid: TimeBasedUUID => uuid.value()
      case other               => throw new IllegalArgumentException("Cassandra does not support " + other.getClass.getName + " offsets")
    }
    queries.eventsByTag(tag, offset)
      .map { env => akka.japi.Pair.create(env.event.asInstanceOf[Event], Offset.timeBasedUUID(env.offset)) }
      .asJava
  }

  override def beforeAll {
    super.beforeAll()
  }

  val readSide = new TestEntityReadSide(testSession)

  def assertSelectCount(id: String, expected: Long): Unit = {
    within(20.seconds) {
      awaitAssert {
        val count = Await.result(readSide.getAppendCount(id).toScala, 5.seconds)
        count should ===(expected)
      }
    }
  }

  def createReadSideProcessor(tag: AggregateEventTag[TestEntity.Evt]) = {
    /* read side and injector only needed for deprecated register method */
    val cassandraReadSide = new CassandraReadSideImpl(system, testSession, null, null)
    val processorFactory = () => new TestEntityReadSide.TestEntityReadSideProcessor(cassandraReadSide, testSession)
    val readSide = system.actorOf(ReadSideActor.props[TestEntity.Evt](
      processorFactory,
      eventStream, classOf[TestEntity.Evt], testActor, 20.seconds
    ))

    readSide ! EnsureActive(tag.tag)

    expectMsg(Prepare)

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

