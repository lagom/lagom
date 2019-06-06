/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#post1
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

final class Post1 extends PersistentEntity {

  override type Command = BlogCommand
  override type Event   = BlogEvent
  override type State   = BlogState

  override def initialState: BlogState = BlogState.empty

  override def behavior: Behavior = Actions()

}
//#post1
