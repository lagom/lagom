/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import java.sql.Connection
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence.{ AggregateEventTag, EventStreamElement, ReadSideProcessor, TestEntity }

import scala.concurrent.Future

object JdbcTestEntityReadSide {

  class TestEntityReadSideProcessor(readSide: JdbcReadSide) extends ReadSideProcessor[TestEntity.Evt] {
    import JdbcSession.tryWith

    def buildHandler(): ReadSideHandler[TestEntity.Evt] =
      readSide.builder[TestEntity.Evt]("test-entity-read-side")
        .setGlobalPrepare(this.createTable)
        .setEventHandler(updateCount _)
        .build()

    private def createTable(connection: Connection): Unit = {
      tryWith(connection.prepareCall("create table if not exists testcounts (id varchar primary key, count bigint)")) {
        _.execute()
      }
    }

    private def updateCount(connection: Connection, event: EventStreamElement[TestEntity.Appended]): Unit = {
      tryWith(connection.prepareStatement("select count from testcounts where id = ?")) { statement =>
        statement.setString(1, event.entityId)
        tryWith(statement.executeQuery) { rs =>
          tryWith(if (rs.next) {
            val count: Long = rs.getLong("count")
            val update = connection.prepareStatement("update testcounts set count = ? where id = ?")
            update.setLong(1, count + 1)
            update.setString(2, event.entityId)
            update
          } else {
            val update = connection.prepareStatement("insert into testcounts values (?, 1)")
            update.setString(1, event.entityId)
            update
          })(_.execute)
        }
      }
    }

    def aggregateTags: Set[AggregateEventTag[Evt]] = TestEntity.Evt.aggregateEventShards.allTags
  }

}

class JdbcTestEntityReadSide(session: JdbcSession) {

  import JdbcSession.tryWith

  def getAppendCount(id: String): Future[Long] = session.withConnection(connection => {
    tryWith(connection.prepareStatement("select count from testcounts where id = ?")) { statement =>
      statement.setString(1, id)

      tryWith(statement.executeQuery()) { rs =>
        if (rs.next()) rs.getLong("count")
        else 0L
      }
    }
  })

}
