/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#full-example
import akka.Done
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

final class Post extends PersistentEntity {

  override type Command = BlogCommand
  override type Event   = BlogEvent
  override type State   = BlogState

  override def initialState: BlogState = BlogState.empty

  override def behavior: Behavior = {
    case state if state.isEmpty  => initial
    case state if !state.isEmpty => postAdded
  }

  private val initial: Actions = {
    Actions()
    // Command handlers are invoked for incoming messages (commands).
    // A command handler must "return" the events to be persisted (if any).
      .onCommand[AddPost, AddPostDone] {
        case (AddPost(content), ctx, state) =>
          if (content.title == null || content.title.equals("")) {
            ctx.invalidCommand("Title must be defined")
            ctx.done
          } else {
            ctx.thenPersist(PostAdded(entityId, content)) { _ =>
              // After persist is done additional side effects can be performed
              ctx.reply(AddPostDone(entityId))
            }
          }
      }
      // Event handlers are used both when persisting new events and when replaying
      // events.
      .onEvent {
        case (PostAdded(postId, content), state) =>
          BlogState(Some(content), published = false)
      }
  }

  private val postAdded: Actions = {
    Actions()
      .onCommand[ChangeBody, Done] {
        case (ChangeBody(body), ctx, state) =>
          ctx.thenPersist(BodyChanged(entityId, body))(_ => ctx.reply(Done))
      }
      .onEvent {
        case (BodyChanged(_, body), state) =>
          state.withBody(body)
      }
      .onReadOnlyCommand[GetPost.type, PostContent] {
        case (GetPost, ctx, state) =>
          ctx.reply(state.content.get)
      }
  }

}
//#full-example
