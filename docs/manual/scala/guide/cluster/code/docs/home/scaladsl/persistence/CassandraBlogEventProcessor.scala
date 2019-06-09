/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#imports
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.Done
import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraReadSide
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import scala.concurrent.Promise

//#imports

trait CassandraBlogEventProcessor {

  trait Initial {
    //#initial
    class BlogEventProcessor(session: CassandraSession, readSide: CassandraReadSide)(implicit ec: ExecutionContext)
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

  class BlogEventProcessor(session: CassandraSession, readSide: CassandraReadSide)(implicit ec: ExecutionContext)
      extends ReadSideProcessor[BlogEvent] {

    //#tag
    override def aggregateTags: Set[AggregateEventTag[BlogEvent]] =
      BlogEvent.Tag.allTags
    //#tag

    //#create-table
    private def createTable(): Future[Done] =
      session.executeCreateTable(
        "CREATE TABLE IF NOT EXISTS blogsummary ( " +
          "id TEXT, title TEXT, PRIMARY KEY (id))"
      )
    //#create-table

    //#prepare-statements
    private val writeTitlePromise                     = Promise[PreparedStatement] // initialized in prepare
    private def writeTitle: Future[PreparedStatement] = writeTitlePromise.future

    private def prepareWriteTitle(): Future[Done] = {
      val f = session.prepare("INSERT INTO blogsummary (id, title) VALUES (?, ?)")
      writeTitlePromise.completeWith(f)
      f.map(_ => Done)
    }
    //#prepare-statements

    //#post-added
    private def processPostAdded(eventElement: EventStreamElement[PostAdded]): Future[List[BoundStatement]] = {
      writeTitle.map { ps =>
        val bindWriteTitle = ps.bind()
        bindWriteTitle.setString("id", eventElement.event.postId)
        bindWriteTitle.setString("title", eventElement.event.content.title)
        List(bindWriteTitle)
      }
    }
    //#post-added

    override def buildHandler(): ReadSideProcessor.ReadSideHandler[BlogEvent] = {
      //#create-builder
      val builder = readSide.builder[BlogEvent]("blogsummaryoffset")
      //#create-builder

      //#register-global-prepare
      builder.setGlobalPrepare(() => createTable())
      //#register-global-prepare

      //#register-prepare
      builder.setPrepare(tag => prepareWriteTitle())
      //#register-prepare

      //#set-event-handler
      builder.setEventHandler[PostAdded](processPostAdded)
      //#set-event-handler

      //#build
      builder.build()
      //#build
    }
  }

}
