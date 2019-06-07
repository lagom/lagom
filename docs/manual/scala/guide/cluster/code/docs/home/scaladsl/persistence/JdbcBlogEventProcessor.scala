/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#imports
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcReadSide
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import scala.concurrent.ExecutionContext
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcSession.tryWith

//#imports

trait JdbcBlogEventProcessor {

  trait Initial {
    //#initial
    class BlogEventProcessor(readSide: JdbcReadSide)(implicit ec: ExecutionContext)
        extends ReadSideProcessor[BlogEvent] {

      override def buildHandler(): ReadSideProcessor.ReadSideHandler[BlogEvent] = {
        // TODO build read side handler
        ???
      }

      override def aggregateTags: Set[AggregateEventTag[BlogEvent]] = {
        // TODO return the tag for the events
        ???
      }
    }
    //#initial
  }

  class BlogEventProcessor(readSide: JdbcReadSide) extends ReadSideProcessor[BlogEvent] {

    //#tag
    override def aggregateTags: Set[AggregateEventTag[BlogEvent]] =
      BlogEvent.Tag.allTags
    //#tag

    //#create-table
    private def createTable(connection: Connection): Unit = {
      tryWith(
        connection.prepareStatement(
          "CREATE TABLE IF NOT EXISTS blogsummary ( " +
            "id VARCHAR(64), title VARCHAR(256), PRIMARY KEY (id))"
        )
      ) { ps =>
        ps.execute()
      }
    }
    //#create-table

    //#post-added
    private def processPostAdded(connection: Connection, eventElement: EventStreamElement[PostAdded]): Unit = {
      tryWith(connection.prepareStatement("INSERT INTO blogsummary (id, title) VALUES (?, ?)")) { statement =>
        statement.setString(1, eventElement.event.postId)
        statement.setString(2, eventElement.event.content.title)
        statement.execute()
      }
    }
    //#post-added

    override def buildHandler(): ReadSideProcessor.ReadSideHandler[BlogEvent] = {
      //#create-builder
      val builder = readSide.builder[BlogEvent]("blogsummaryoffset")
      //#create-builder

      //#register-global-prepare
      builder.setGlobalPrepare(createTable)
      //#register-global-prepare

      //#set-event-handler
      builder.setEventHandler[PostAdded](processPostAdded)
      //#set-event-handler

      //#build
      builder.build()
      //#build
    }
  }

}
