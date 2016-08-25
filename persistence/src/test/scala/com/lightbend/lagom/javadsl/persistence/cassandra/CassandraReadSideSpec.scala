/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.{ List => JList }
import java.util.Optional
import java.util.UUID
import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.function.{ Supplier, Function => JFunction }

import akka.{ Done, NotUsed }
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source

import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.PreparedStatement
import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.internal.persistence.{ PersistentEntityActor, ReadSideActor }
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraReadSideImpl, CassandraSessionImpl }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.javadsl.persistence.Offset.TimeBasedUUID
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler
import org.pcollections.{ PSequence, TreePVector }

object CassandraReadSideSpec {

  val config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    """)

  class TestEntityProcessor(session: CassandraSession, readSide: CassandraReadSide)(implicit ec: ExecutionContext) extends ReadSideProcessor[TestEntity.Evt] {

    import CassandraReadSide._

    var count = 0L
    var writeStmt: PreparedStatement = _

    override def aggregateTags: PSequence[AggregateEventTag[TestEntity.Evt]] =
      TreePVector.singleton(TestEntity.Evt.aggregateTag)

    override def buildHandler(): ReadSideHandler[TestEntity.Evt] = {
      readSide.builder[TestEntity.Evt]("testoffsets")
        .setPrepare(prepare)
        .setEventHandler(classOf[TestEntity.Appended], handleAppended)
        .build()
    }

    val prepare = new Supplier[CompletionStage[Done]] {
      override def get() = {
        def prepareWriteStmt(): Future[Unit] =
          session.prepare("INSERT INTO testcounts (id, count) VALUES (?, ?)").toScala
            .map(writeStmt = _)

        def selectCount(): Future[Unit] = {
          for {
            selectStmt <- session.prepare("SELECT count, offset FROM testcounts WHERE id = ?").toScala
            bs = selectStmt.bind("test")
            row <- session.selectOne(bs).toScala
          } yield {
            if (row.isPresent()) {
              count = row.get.getLong("count")
            }
          }
        }

        (for {
          _ <- prepareWriteStmt()
          _ <- selectCount()
        } yield Done.getInstance()).toJava
      }
    }

    val handleAppended = new JFunction[TestEntity.Appended, CompletionStage[JList[BoundStatement]]] {
      override def apply(event: TestEntity.Appended): CompletionStage[JList[BoundStatement]] = {
        count += 1
        val bs = writeStmt.bind()
        bs.setString("id", "test")
        bs.setLong("count", count)
        completedStatement(bs)
      }
    }

  }
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
    createTable()
  }

  def createTable(): Unit = {
    Await.ready(testSession.executeCreateTable(s"""
      CREATE TABLE IF NOT EXISTS testcounts (
        id text,
        count bigint,
        offset timeuuid,
        PRIMARY KEY (id))
        """).toScala, 15.seconds)
  }

  lazy val selectStmt: PreparedStatement =
    Await.result(testSession.prepare("SELECT count FROM testcounts WHERE id = ?").toScala, 5.seconds)

  def assertSelectCount(expected: Long): Unit = {
    within(20.seconds) {
      awaitAssert {
        val count = Await.result(testSession.selectOne(selectStmt.bind("test")).toScala.map(_.get.getLong("count")), 5.seconds)
        count should ===(expected)
      }
    }
  }

  def createReadSide() = {
    /* read side and injector only needed for deprecated register method */
    val cassandraReadSide = new CassandraReadSideImpl(system, testSession, null, null)
    val readSide = system.actorOf(ReadSideActor.props[TestEntity.Evt](
      () => new TestEntityProcessor(testSession, cassandraReadSide),
      eventStream, classOf[TestEntity.Evt]
    ))

    readSide ! EnsureActive(TestEntity.Evt.aggregateTag.tag)

    readSide
  }

  "ReadSide" must {

    "use correct tag" in {
      new TestEntity.Appended("A").aggregateTag.tag should ===(classOf[TestEntity.Evt].getName)
      TestEntity.InPrependMode.instance().aggregateTag.tag should ===(classOf[TestEntity.Evt].getName)
    }

    "process events and save query projection" in {
      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! TestEntity.Add.of("a")
      expectMsg(new TestEntity.Appended("A"))
      p ! TestEntity.Add.of("b")
      expectMsg(new TestEntity.Appended("B"))
      p ! TestEntity.Add.of("c")
      expectMsg(new TestEntity.Appended("C"))

      val readSide = createReadSide()

      assertSelectCount(3L)

      p ! TestEntity.Add.of("d")
      expectMsg(new TestEntity.Appended("D"))

      assertSelectCount(4L)

      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

    "resume from stored offset" in {
      // count = 4 from previous test step
      assertSelectCount(4L)

      val readSide = createReadSide()

      val p = system.actorOf(PersistentEntityActor.props("test", Optional.of("1"),
        () => new TestEntity(system), Optional.empty(), 10.seconds))
      p ! TestEntity.Add.of("e")
      expectMsg(new TestEntity.Appended("E"))

      assertSelectCount(5L)

      watch(readSide)
      system.stop(readSide)
      expectTerminated(readSide)
    }

  }

}

