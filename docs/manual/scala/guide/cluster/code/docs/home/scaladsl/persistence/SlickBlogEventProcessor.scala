/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#imports
import akka.Done
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import docs.home.scaladsl.persistence.SlickRepos.Full.PostSummaryRepository
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext
//#imports

trait SlickBlogEventProcessor {

  trait Initial {
    //#initial
    class BlogEventProcessor(
        readSide: SlickReadSide,
        postSummaryRepo: PostSummaryRepository
    ) extends ReadSideProcessor[BlogEvent] {

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

  trait Final {

    class BlogEventProcessor(
        readSide: SlickReadSide,
        postSummaryRepo: PostSummaryRepository
    ) extends ReadSideProcessor[BlogEvent] {

      //#tag
      override def aggregateTags: Set[AggregateEventTag[BlogEvent]] =
        BlogEvent.Tag.allTags
      //#tag

      //#post-added
      private def processPostAdded(eventElement: EventStreamElement[PostAdded]): DBIO[Done] = {
        postSummaryRepo.save(
          PostSummary(
            eventElement.event.postId,
            eventElement.event.content.title
          )
        )
      }
      //#post-added

      override def buildHandler(): ReadSideProcessor.ReadSideHandler[BlogEvent] = {
        //#create-builder
        val builder = readSide.builder[BlogEvent]("blogsummaryoffset")
        //#create-builder

        //#register-global-prepare
        builder.setGlobalPrepare(postSummaryRepo.createTable)
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

}
