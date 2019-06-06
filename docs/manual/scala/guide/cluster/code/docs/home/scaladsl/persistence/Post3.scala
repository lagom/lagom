/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

final class Post3 extends PersistentEntity {

  override type Command = BlogCommand
  override type Event   = BlogEvent
  override type State   = BlogState

  override def initialState: BlogState = BlogState.empty

  //#same-behavior
  override def behavior: Behavior =
    Actions()
      .onCommand[AddPost, AddPostDone] {
        case (AddPost(content), ctx, state) if state.isEmpty =>
          ctx.thenPersist(PostAdded(entityId, content)) { evt =>
            ctx.reply(AddPostDone(entityId))
          }
      }
      .onEvent {
        case (PostAdded(postId, content), state) =>
          BlogState(Some(content), published = false)
      }
      .onReadOnlyCommand[GetPost.type, PostContent] {
        case (GetPost, ctx, state) if !state.isEmpty =>
          ctx.reply(state.content.get)
      }
  //#same-behavior
}
