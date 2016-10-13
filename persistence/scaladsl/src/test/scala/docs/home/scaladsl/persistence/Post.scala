/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.persistence

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

// FIXME move to docs project when API has settled

object Post {

  // FIXME Jsonable
  sealed trait BlogCommand

  final case class AddPost(content: PostContent) extends BlogCommand with ReplyType[AddPostDone]
  final case class AddPostDone(postId: String)
  case object GetPost extends BlogCommand with ReplyType[PostContent]
  final case class ChangeBody(body: String) extends BlogCommand with ReplyType[Done]
  case object Publish extends BlogCommand with ReplyType[Done]

  object BlogEvent {
    val NumShards = 20
    // second param is optional, defaults to the class name
    val aggregateEventShards = AggregateEventTag.sharded(classOf[BlogEvent], NumShards)
  }

  sealed trait BlogEvent extends AggregateEvent[BlogEvent] {
    override def aggregateTag: AggregateEventShards[BlogEvent] = BlogEvent.aggregateEventShards
  }

  final case class PostAdded(postId: String, content: PostContent) extends BlogEvent
  final case class BodyChanged(postId: String, body: String) extends BlogEvent
  final case class PostPublished(postId: String) extends BlogEvent

  final case class PostContent(title: String, body: String)

  object BlogState {
    val empty = BlogState(None, published = false)

  }

  final case class BlogState(content: Option[PostContent], published: Boolean) {
    def withBody(body: String): BlogState = {
      content match {
        case Some(c) =>
          copy(content = Some(c.copy(body = body)))
        case None =>
          throw new IllegalStateException("Can't set body without content")
      }
    }

    def isEmpty: Boolean = content.isEmpty
  }

}

final class Post extends PersistentEntity[Post.BlogCommand, Post.BlogEvent, Post.BlogState] {
  import Post._

  override def initialBehavior(snapshot: Option[BlogState]): Behavior = {
    snapshot match {
      case Some(snap) if !snap.isEmpty =>
        // behavior after snapshot must be restored by initialBehavior
        becomePostAdded(snap)
      case _ =>
        // Behavior consist of a State and defined event handlers and command handlers.
        Behavior(BlogState.empty)
          // Command handlers are invoked for incoming messages (commands).
          // A command handler must "return" the events to be persisted (if any).
          .addCommandHandler {
            case (cmd @ AddPost(content), ctx, state) =>
              if (content.title == null || content.title.equals("")) {
                ctx.invalidCommand("Title must be defined")
                ctx.done
              } else {
                ctx.thenPersist(PostAdded(entityId, content), evt =>
                  // After persist is done additional side effects can be performed
                  ctx.reply(cmd, AddPostDone(entityId)))
              }
          }
          // Event handlers are used both when persisting new events and when replaying
          // events.
          .addEventHandler {
            case (PostAdded(postId, content), behavior) =>
              becomePostAdded(BlogState(Some(content), published = false))
          }
    }

  }

  private def becomePostAdded(state: BlogState): Behavior = {
    Behavior(state)
      .addCommandHandler {
        case (cmd @ ChangeBody(body), ctx, state) =>
          ctx.thenPersist(BodyChanged(entityId, body), _ => ctx.reply(cmd, Done))
      }
  }

}
