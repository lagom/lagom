/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.persistence.ReadSide

import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.Service
import akka.stream.scaladsl.Source
import akka.persistence.query.NoOffset
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import scala.concurrent.Future

trait BlogServiceImpl3 {
  trait BlogService extends Service {
    def newPosts(): ServiceCall[NotUsed, Source[PostSummary, _]]

    override def descriptor = ???
  }

  //#register-event-processor
  class BlogServiceImpl(persistentEntityRegistry: PersistentEntityRegistry, readSide: ReadSide, myDatabase: MyDatabase)
      extends BlogService {

    readSide.register[BlogEvent](new BlogEventProcessor(myDatabase))
    //#register-event-processor

    //#event-stream
    override def newPosts(): ServiceCall[NotUsed, Source[PostSummary, _]] =
      ServiceCall { request =>
        val response: Source[PostSummary, NotUsed] =
          persistentEntityRegistry
            .eventStream(BlogEvent.Tag.forEntityId(""), NoOffset)
            .collect {
              case EventStreamElement(entityId, event: PostAdded, offset) =>
                PostSummary(event.postId, event.content.title)
            }
        Future.successful(response)
      }
    //#event-stream
  }
}
