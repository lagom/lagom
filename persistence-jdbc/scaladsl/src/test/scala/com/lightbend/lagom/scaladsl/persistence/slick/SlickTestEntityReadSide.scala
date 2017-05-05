/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.{ AggregateEventTag, EventStreamElement, ReadSideProcessor, TestEntity }

import scala.concurrent.{ ExecutionContext, Future }
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

trait Tables {

  val profile: JdbcProfile
  import profile.api._
  implicit val ec: ExecutionContext

  case class TestCount(id: String, count: Long)
  class TestCounts(tag: Tag) extends Table[TestCount](tag, "testcounts") {
    def id = column[String]("id", O.PrimaryKey)
    def count = column[Long]("count")
    def * = (id, count) <> (TestCount.tupled, TestCount.unapply)
  }
  lazy val testCounts: TableQuery[TestCounts] = TableQuery[TestCounts]

  def createTable: DBIO[_] = testCounts.schema.create

  def countUpdate(id: String, diff: Int = 1): DBIO[_] = {
    val q: Query[TestCounts, TestCount, Seq] = testCounts.filter(_.id === id)
    for {
      select <- q.result
      updated <- select.headOption match {
        case Some(testCount) =>
          q.update(testCount.copy(count = testCount.count + diff))
        case None =>
          testCounts += TestCount(id, diff)
      }
    } yield updated
  }
}

object SlickTestEntityReadSide {

  class TestEntityReadSideProcessor(readSide: SlickReadSide, db: Database, val profile: JdbcProfile)(implicit val ec: ExecutionContext)
    extends ReadSideProcessor[TestEntity.Evt]
    with Tables {

    def buildHandler(): ReadSideHandler[TestEntity.Evt] = readSide
      .builder[TestEntity.Evt]("test-entity-read-side")
      .setGlobalPrepare(createTable)
      .setEventHandler(updateCount)
      .build()

    def aggregateTags: Set[AggregateEventTag[Evt]] = TestEntity.Evt.aggregateEventShards.allTags

    def updateCount(event: EventStreamElement[TestEntity.Appended]) = countUpdate(event.entityId, 1)
  }
}

class SlickTestEntityReadSide(db: Database, val profile: JdbcProfile)(implicit val ec: ExecutionContext)
  extends Tables {

  import profile.api._

  def getAppendCount(id: String): Future[Long] = db.run {
    testCounts.filter(_.id === id)
      .map(_.count)
      .result
      .headOption
      .map(_.getOrElse(0l))
  }
}
