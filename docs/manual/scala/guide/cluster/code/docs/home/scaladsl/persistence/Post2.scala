/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

final class Post2 extends PersistentEntity {

  override type Command = BlogCommand
  override type Event   = BlogEvent
  override type State   = BlogState

  override def initialState: BlogState = BlogState.empty

  //#behavior
  override def behavior: Behavior = {
    case state if state.isEmpty  => initial
    case state if !state.isEmpty => postAdded
  }
  //#behavior

  //#initial-actions
  private val initial: Actions = {
    //#initial-actions
    //#command-handler
    // Command handlers are invoked for incoming messages (commands).
    // A command handler must "return" the events to be persisted (if any).
    Actions()
      .onCommand[AddPost, AddPostDone] {
        case (AddPost(content), ctx, state) =>
          ctx.thenPersist(PostAdded(entityId, content)) { evt =>
            // After persist is done additional side effects can be performed
            ctx.reply(AddPostDone(entityId))
          }
      }
      //#command-handler
      //#validate-command
      // Command handlers are invoked for incoming messages (commands).
      // A command handler must "return" the events to be persisted (if any).
      .onCommand[AddPost, AddPostDone] {
        case (AddPost(content), ctx, state) =>
          if (content.title == null || content.title.equals("")) {
            ctx.invalidCommand("Title must be defined")
            ctx.done
          }
          //#validate-command
          else {
            ctx.thenPersist(PostAdded(entityId, content)) { _ =>
              // After persist is done additional side effects can be performed
              ctx.reply(AddPostDone(entityId))
            }
          }
      }
      //#validate-command
      //#event-handler
      // Event handlers are used both when persisting new events and when replaying
      // events.
      .onEvent {
        case (PostAdded(postId, content), state) =>
          BlogState(Some(content), published = false)
      }
    //#event-handler
  }

  //#postAdded-actions
  private val postAdded: Actions = {
    //#postAdded-actions
    Actions()
    //#reply
      .onCommand[ChangeBody, Done] {
        case (ChangeBody(body), ctx, state) =>
          ctx.thenPersist(BodyChanged(entityId, body))(_ => ctx.reply(Done))
      }
      //#reply
      .onEvent {
        case (BodyChanged(_, body), state) =>
          state.withBody(body)
      }
      //#read-only-command-handler
      .onReadOnlyCommand[GetPost.type, PostContent] {
        case (GetPost, ctx, state) =>
          ctx.reply(state.content.get)
      }
    //#read-only-command-handler
  }

}
