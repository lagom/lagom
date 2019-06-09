/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag

class ShardedBlogEventTag {

  //#sharded-tags
  object BlogEvent {
    val NumShards = 20
    val Tag       = AggregateEventTag.sharded[BlogEvent](NumShards)
  }

  sealed trait BlogEvent extends AggregateEvent[BlogEvent] {
    override def aggregateTag: AggregateEventShards[BlogEvent] = BlogEvent.Tag
  }
  //#sharded-tags
}
