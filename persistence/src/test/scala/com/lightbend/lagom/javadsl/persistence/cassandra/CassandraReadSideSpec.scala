/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.{ List => JList }
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.japi.function.Creator
import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.PreparedStatement
import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.javadsl.persistence.PersistenceSpec
import com.lightbend.lagom.internal.persistence.PersistentEntityActor
import com.lightbend.lagom.javadsl.persistence.TestEntity
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.internal.persistence.PersistentEntityActor
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideActor
import com.lightbend.lagom.internal.persistence.cassandra.CassandraSessionImpl
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag

object CassandraReadSideSpec {

  val config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    """)

  class TestEntityProcessor(implicit ec: ExecutionContext) extends CassandraReadSideProcessor[TestEntity.Evt] {

    var count = 0L
    var writeStmt: PreparedStatement = _

    override def aggregateTag: AggregateEventTag[TestEntity.Evt] =
      TestEntity.Evt.aggregateTag

    override def prepare(session: CassandraSession): CompletionStage[Optional[UUID]] = {
      def prepareWriteStmt(): Future[Unit] =
        session.prepare("INSERT INTO testcounts (id, count, offset) VALUES (?, ?, ?)").toScala
          .map(writeStmt = _)

      def selectCountAndOffset(): Future[Optional[UUID]] = {
        for {
          selectStmt <- session.prepare("SELECT count, offset FROM testcounts WHERE id = ?").toScala
          bs = selectStmt.bind("test")
          row <- session.selectOne(bs).toScala
        } yield {
          if (row.isPresent()) {
            count = row.get.getLong("count")
            Optional.ofNullable(row.get.getUUID("offset"))
          } else
            Optional.empty()
        }
      }

      (for {
        _ <- prepareWriteStmt()
        offset <- selectCountAndOffset()
      } yield offset).toJava
    }

    override def defineEventHandlers(builder: EventHandlersBuilder): EventHandlers = {
      builder.setEventHandler(classOf[TestEntity.Appended], handleAppended)
      builder.build()
    }

    val handleAppended = new BiFunction[TestEntity.Appended, UUID, CompletionStage[JList[BoundStatement]]] {
      override def apply(event: TestEntity.Appended, offset: UUID): CompletionStage[JList[BoundStatement]] = {
        count += 1
        val bs = writeStmt.bind()
        bs.setString("id", "test")
        bs.setLong("count", count)
        bs.setUUID("offset", offset)
        completedStatement(bs)
      }
    }

  }
}

class CassandraReadSideSpec extends PersistenceSpec(CassandraReadSideSpec.config) {
  import CassandraReadSideSpec._
  import system.dispatcher

  lazy val testSession: CassandraSession = new CassandraSessionImpl(system)

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

      val readSide = system.actorOf(CassandraReadSideActor.props[TestEntity.Evt](
        classOf[TestEntity.Evt].getName, testSession, () => new TestEntityProcessor
      ))

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

      val readSide = system.actorOf(CassandraReadSideActor.props[TestEntity.Evt](
        classOf[TestEntity.Evt].getName, testSession, () => new TestEntityProcessor
      ))

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

