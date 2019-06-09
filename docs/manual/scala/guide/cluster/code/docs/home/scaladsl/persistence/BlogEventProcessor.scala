/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import scala.concurrent.Future

import akka.Done
import akka.NotUsed
import akka.persistence.query._
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler

//#my-database
trait MyDatabase {

  /**
   * Create the tables needed for this read side if not already created.
   */
  def createTables(): Future[Done]

  /**
   * Load the offset of the last event processed.
   */
  def loadOffset(tag: AggregateEventTag[BlogEvent]): Future[Offset]

  /**
   * Handle the post added event.
   */
  def handleEvent(event: BlogEvent, offset: Offset): Future[Done]
}
//#my-database

object MyDatabase extends MyDatabase {
  def createTables(): Future[Done]                                  = Future.successful(Done)
  def loadOffset(tag: AggregateEventTag[BlogEvent]): Future[Offset] = Future.successful(NoOffset)
  def handleEvent(event: BlogEvent, offset: Offset): Future[Done]   = Future.successful(Done)
}

class BlogEventProcessor(myDatabase: MyDatabase) extends ReadSideProcessor[BlogEvent] {

  //#tag
  override def aggregateTags: Set[AggregateEventTag[BlogEvent]] =
    BlogEvent.Tag.allTags
  //#tag

  //#build-handler
  override def buildHandler(): ReadSideProcessor.ReadSideHandler[BlogEvent] = {
    new ReadSideHandler[BlogEvent] {

      override def globalPrepare(): Future[Done] =
        myDatabase.createTables()

      override def prepare(tag: AggregateEventTag[BlogEvent]): Future[Offset] =
        myDatabase.loadOffset(tag)

      override def handle(): Flow[EventStreamElement[BlogEvent], Done, NotUsed] = {
        Flow[EventStreamElement[BlogEvent]]
          .mapAsync(1) { eventElement =>
            myDatabase.handleEvent(eventElement.event, eventElement.offset)
          }
      }
    }
  }
  //#build-handler

}
